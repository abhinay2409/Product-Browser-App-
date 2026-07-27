# Product Browser — Kotlin Multiplatform

A cross-platform product catalog prototype built with **Kotlin Multiplatform** and **Compose Multiplatform**, targeting **Android** and **iOS** from a single shared codebase. Product data comes from the public [DummyJSON Products API](https://dummyjson.com/docs/products).

## Business Requirements

The app lets users:

1. **Browse products** — a list showing name, price, rating and thumbnail.
2. **View product details** — title, description, brand, price (with discount), rating, stock and category.
3. **Search by keyword** — wired to the API's `/products/search` endpoint with client-side debouncing.
4. **Filter by category** *(bonus)* — chips backed by `/products/categories` and `/products/category/{slug}`.

Additional bonus items: **local (in-memory TTL) caching** and a **shared `@Preview`**.

## Tech Stack

| Concern | Choice |
|---|---|
| Language | Kotlin `2.3.21` |
| Shared UI | Compose Multiplatform `1.11.1` |
| Networking | Ktor Client `3.5.0` (OkHttp on Android, Darwin on iOS) |
| JSON | kotlinx.serialization `1.11.0` |
| Async / state | Coroutines `1.11.0`, `StateFlow` |
| Images | Coil `3.5.0` |
| DI | Manual (`AppContainer`) |
| Build | AGP `8.13.2`, Gradle `8.14` |

## Architecture Overview

Clean Architecture in one shared module. **Dependencies point inward**: presentation → domain ← data. The domain layer imports no framework code at all.

```
composeApp/src/commonMain/kotlin/com/revest/productbrowser
├── domain/          # models, repository interface, use cases  (pure Kotlin)
├── data/            # Ktor API, DTOs, mapper, cache, repository impl
├── presentation/    # screens, ViewModels, components, navigation
├── di/              # manual DI container
└── App.kt           # root composable + navigation

androidMain/         # MainActivity, BackHandler actual, OkHttp engine
iosMain/             # MainViewController, BackHandler actual, Darwin engine
commonTest/          # unit tests (fake repository + Ktor MockEngine)
iosApp/              # Xcode project (SwiftUI host)
```

---

# Code Walkthrough

Every file, in the order it was built. Each entry explains **what it does**, **why it exists**, and **the lines worth understanding**.

## Part 1 — Build configuration

### `gradle/libs.versions.toml`

A **version catalog**: one file holding every dependency version and coordinate, referenced elsewhere as `libs.ktor.client.core`.

Why it matters in KMP: Kotlin, Compose Multiplatform, AGP and the lifecycle artifacts all have compatibility constraints with each other. Centralising versions makes an upgrade a one-line change and prevents two modules drifting apart.

One non-obvious entry:

```toml
compose-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "composeMaterialIcons" }
```

Material icons are no longer bundled with `material3` in recent Compose Multiplatform releases — they ship as a separate artifact. Without this, `Icons.Default.Search` won't resolve.

### `composeApp/build.gradle.kts`

Declares the targets and wires dependencies per source set.

```kotlin
androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_11) } }

listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
    iosTarget.binaries.framework {
        baseName = "ComposeApp"
        isStatic = true
    }
}
```

Three iOS targets cover Intel simulators, real devices, and Apple-silicon simulators. They all produce a framework called `ComposeApp` — that's the name Xcode links against and the name Swift imports.

The source-set block is where the multiplatform magic is:

```kotlin
commonMain.dependencies { implementation(libs.ktor.client.core) /* … */ }
androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
iosMain.dependencies   { implementation(libs.ktor.client.darwin) }
```

Common code only knows the Ktor *core* API. Each platform contributes its own HTTP engine, and Ktor picks it up automatically at runtime — **no `expect`/`actual` needed for networking**.

### `settings.gradle.kts` / root `build.gradle.kts` / `gradle.properties`

`settings.gradle.kts` declares repositories and includes the single `:composeApp` module. The root build file registers plugins with `apply false` so they share one classpath but are applied per-module. `gradle.properties` enables the Gradle build cache and configuration cache, and raises JVM memory — KMP builds are heavier than pure Android ones.

---

## Part 2 — Domain layer (pure Kotlin, zero dependencies)

### `domain/model/Product.kt`

The app's own idea of a product — deliberately **not** the API's shape.

```kotlin
data class Product(
    val id: Int,
    val title: String,
    /* … */
    val brand: String?,
    val images: List<String>
) {
    val hasDiscount: Boolean get() = discountPercentage > 0.0

    val originalPrice: Double
        get() = if (hasDiscount) price / (1 - discountPercentage / 100) else price
}
```

Two things to notice:

- **`brand` is nullable.** Some DummyJSON products genuinely have no brand field. Modelling that honestly here means no crash later.
- **`originalPrice` is computed, not stored.** The API only sends the discounted price and a percentage; reconstructing the pre-discount price is a *business rule*, so it lives in the domain model rather than being recalculated inside a composable.

Keeping this class free of `@Serializable` is intentional — if the API renames a field tomorrow, only the DTO changes.

### `domain/model/Category.kt`

A two-field model. `slug` is what the API expects in URLs (`"home-decoration"`); `name` is what humans read (`"Home Decoration"`). Keeping both avoids string-munging in the UI.

### `domain/repository/ProductRepository.kt`

The contract between domain and data — an **interface owned by the domain**, implemented by the data layer. This is the dependency inversion that makes the arrow point inward.

```kotlin
interface ProductRepository {
    suspend fun getProducts(limit: Int = DEFAULT_PAGE_SIZE, skip: Int = 0): Result<List<Product>>
    suspend fun searchProducts(query: String): Result<List<Product>>
    suspend fun getProduct(id: Int): Result<Product>
    suspend fun getCategories(): Result<List<Category>>
    suspend fun getProductsByCategory(categorySlug: String): Result<List<Product>>
}
```

Every method returns **`Result<T>`, not a bare value**. This is a deliberate rule: *exceptions never cross layer boundaries*. Failures travel as data, so the ViewModel handles them with `onSuccess`/`onFailure` instead of `try/catch`, and it's impossible to forget an error path.

`suspend` (not `Flow`) because these are one-shot requests, not streams.

### `domain/usecase/` — five files

Each use case is a single-responsibility class holding one business operation.

```kotlin
class GetProductsUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(
        limit: Int = ProductRepository.DEFAULT_PAGE_SIZE,
        skip: Int = 0
    ): Result<List<Product>> = repository.getProducts(limit, skip)
}
```

`operator fun invoke` means the call site reads `getProducts()` instead of `getProducts.execute()` — the object behaves like a function.

Most are thin pass-throughs, and that's fine: they give the ViewModel a stable vocabulary and a place for rules to land later. The one that already earns its keep:

```kotlin
class SearchProductsUseCase(private val repository: ProductRepository) {
    suspend operator fun invoke(query: String): Result<List<Product>> {
        val normalized = query.trim()
        return if (normalized.isEmpty()) {
            repository.getProducts()          // blank search = show catalog
        } else {
            repository.searchProducts(normalized)
        }
    }
}
```

Two real decisions live here: whitespace is trimmed before hitting the network, and a blank query means "show everything" rather than firing a pointless empty search. Because this is a plain class with an injected interface, both rules are trivially unit-testable — no Android, no network, no Compose.

---

## Part 3 — Data layer

### `data/remote/dto/ProductDto.kt`

The **wire format** — a mirror of the API's JSON, isolated from the rest of the app.

```kotlin
@Serializable
data class ProductDto(
    val id: Int,
    val title: String = "",
    /* … */
    val brand: String? = null,
    val images: List<String> = emptyList()
)
```

Default values everywhere make parsing resilient: a missing field yields a sensible default instead of an exception. `ProductsResponseDto` wraps the list because DummyJSON returns `{ "products": [...], "total": …, "skip": …, "limit": … }` rather than a bare array.

This separation is the reason an API change can't ripple through the app: it stops at the mapper.

### `data/remote/HttpClientFactory.kt`

One place that configures Ktor.

```kotlin
private fun HttpClientConfig<*>.configure() {
    expectSuccess = true

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        })
    }
    install(HttpTimeout) { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }
    install(Logging) { level = LogLevel.INFO }
}
```

- `expectSuccess = true` makes non-2xx responses throw `ResponseException`, so error handling lives in one place instead of scattered status checks.
- `ignoreUnknownKeys = true` means the app survives the API adding new fields.
- There are **two factory methods**: `create()` for production (engine auto-discovered per platform) and `create(engine)` so tests can inject `MockEngine`. That second overload is what makes the repository testable without a network.

### `data/remote/ProductApi.kt`

A thin typed wrapper over the five endpoints. It knows about HTTP and nothing else — no caching, no domain models.

```kotlin
suspend fun searchProducts(query: String): ProductsResponseDto =
    client.get("$baseUrl/products/search") {
        parameter("q", query)
    }.body()
```

`parameter()` handles URL encoding, so a search for `"a b&c"` can't corrupt the request. `baseUrl` is a constructor parameter with a default — handy for pointing tests or a staging build elsewhere.

### `data/mapper/ProductMapper.kt`

Extension functions converting DTO → domain:

```kotlin
fun ProductDto.toDomain(): Product = Product(id = id, title = title, /* … */)
```

Small and boring on purpose. It's the single choke point where wire format becomes app model — if the API renames `thumbnail`, exactly one line changes.

### `data/cache/InMemoryCache.kt`

A tiny coroutine-safe TTL cache (the local-caching bonus).

```kotlin
private val mutex = Mutex()
private val entries = mutableMapOf<K, Entry<V>>()

suspend fun get(key: K): V? = mutex.withLock {
    val entry = entries[key] ?: return@withLock null
    if (entry.createdAt.elapsedNow() > ttl) {
        entries.remove(key); null
    } else entry.value
}
```

- **`Mutex` + `withLock`**, not `synchronized` — a suspending lock that doesn't block a thread, and works on iOS where JVM concurrency primitives don't exist.
- **`TimeSource.Monotonic`** rather than wall-clock time, so a user changing their device clock can't confuse expiry.
- Expired entries are removed lazily on read — no background cleanup needed for a cache this size.

Generic in `<K, V>`, so the same class serves product lists, single products, and categories.

### `data/repository/ProductRepositoryImpl.kt`

Where network, cache, mapping and error handling meet.

```kotlin
override suspend fun getProducts(limit: Int, skip: Int): Result<List<Product>> = safeCall {
    val cacheKey = "all:$limit:$skip"
    listCache.get(cacheKey) ?: api.getProducts(limit, skip)
        .products.map { it.toDomain() }
        .also { listCache.put(cacheKey, it) }
}
```

Read it right to left: try the cache; on a miss, call the API, map DTOs to domain models, and store the result. The cache key includes the paging parameters so page 1 and page 2 don't overwrite each other. **Search deliberately skips the cache** — search results should always be live.

The error funnel:

```kotlin
private suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e                      // never swallow coroutine cancellation
    } catch (e: Exception) {
        Result.failure(DataException(e.toReadableMessage(), e))
    }
```

The `CancellationException` rethrow is the subtle, important line. When a coroutine is cancelled (user navigates away, search debounce restarts), Kotlin signals it by throwing that exception. Catching it would break structured concurrency and leak work. Everything else becomes a `Result.failure` carrying a message a user can actually read:

```kotlin
private fun Exception.toReadableMessage(): String = when (this) {
    is HttpRequestTimeoutException -> "The request timed out. Please try again."
    is ResponseException -> "Server error (${response.status.value}). Please try again."
    is IOException -> "Couldn't reach the server. Check your internet connection."
    else -> message ?: "Something went wrong. Please try again."
}
```

Note `kotlinx.io.IOException` — in Ktor 3 that's the multiplatform IO exception, not `java.io.IOException`.

---

## Part 4 — Presentation layer

### `di/AppContainer.kt`

Manual dependency injection (the task allows it; Koin/Hilt would be the next step).

```kotlin
class AppContainer {
    private val httpClient = HttpClientFactory.create()
    private val api = ProductApi(httpClient)
    private val repository: ProductRepository = ProductRepositoryImpl(api)

    val getProducts = GetProductsUseCase(repository)
    val searchProducts = SearchProductsUseCase(repository)
    /* … */
}
```

The graph is built once at the app root and passed down. `repository` is typed as the **interface**, not the implementation — swapping in a fake is a one-line change. Wiring is explicit and readable, which is exactly what you want at this size.

### `presentation/list/ProductListUiState.kt`

One immutable data class describing everything the screen can show.

```kotlin
data class ProductListUiState(
    val isLoading: Boolean = false,
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: Category? = null,
    val searchQuery: String = "",
    val error: String? = null
) {
    val isEmpty: Boolean get() = !isLoading && error == null && products.isEmpty()
}
```

A single state object means the UI can never contradict itself the way several independent `mutableStateOf` flags can. `isEmpty` is derived rather than stored, so it can't fall out of sync — "empty" only means *finished loading, no error, no results*.

### `presentation/list/ProductListViewModel.kt`

```kotlin
private val _uiState = MutableStateFlow(ProductListUiState())
val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()
```

The classic pattern: a private mutable flow, a public read-only one. The UI can observe but never mutate.

The debounce:

```kotlin
fun onSearchQueryChange(query: String) {
    _uiState.update { it.copy(searchQuery = query) }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
        delay(SEARCH_DEBOUNCE_MS)   // 400 ms
        executeSearch()
    }
}
```

The text updates immediately (it's just state, so typing feels instant), but each keystroke cancels the previous pending job. Only after 400 ms of quiet does a request go out — so "phone" costs one network call, not five. Job-cancellation debounce keeps the logic readable without extra Flow operators.

`_uiState.update { it.copy(...) }` is used everywhere instead of `.value =` — it's atomic, so concurrent updates from different coroutines can't clobber each other.

`loadCategories()` deliberately ignores failures: category chips are an enhancement, and a failure there shouldn't blank the product list.

This class extends the **multiplatform** `androidx.lifecycle.ViewModel`, so `viewModelScope` works identically on Android and iOS and coroutines are cancelled automatically when the screen goes away.

### `presentation/detail/ProductDetailViewModel.kt`

Same shape, simpler: takes the `productId` as a constructor parameter, loads once in `init`, exposes `ProductDetailUiState(isLoading, product, error)` and a `retry()`.

### `presentation/util/Format.kt`

```kotlin
fun Double.toPriceString(): String {
    val cents = (this * 100).roundToInt()
    return "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
}
```

Hand-rolled because `java.text.NumberFormat` doesn't exist on iOS. A concrete example of the KMP constraint: common code can only use the Kotlin standard library, not the JDK.

### `presentation/components/CommonViews.kt`

`LoadingView`, `ErrorView` (with a Retry button), `EmptyView`, and `RatingBar`. Every one takes a `Modifier` parameter — standard Compose practice so callers control layout. `RatingBar` draws five stars, tinting the first `rating.toInt()` of them, and the components are shared by both screens so states look consistent across the app.

### `presentation/list/ProductListScreen.kt`

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

Collects the `StateFlow` in a lifecycle-aware way — collection pauses when the screen isn't visible, so no work happens off-screen.

The state machine:

```kotlin
when {
    state.isLoading -> LoadingView()
    state.error != null -> ErrorView(state.error.orEmpty(), viewModel::retry)
    state.isEmpty -> EmptyView("No products found. Try a different search.")
    else -> LazyColumn { items(state.products, key = { it.id }) { … } }
}
```

Four mutually exclusive branches, in priority order — there is no fifth state the UI can accidentally land in.

`items(..., key = { it.id })` gives each row a stable identity so Compose reuses and animates rows correctly when the list changes. `AsyncImage` (Coil) handles thumbnail loading, caching and the crossfade.

The file also contains a `@Preview` composable — a shared preview that works from the common source set (a bonus item).

### `presentation/detail/ProductDetailScreen.kt`

Scrollable detail view: hero image, title, brand, `RatingBar`, price with the strikethrough `originalPrice` when discounted, a category chip, stock status (red when zero), and the description. `Icons.AutoMirrored.Filled.ArrowBack` is used so the arrow flips automatically in right-to-left locales.

### `presentation/navigation/PlatformBackHandler.kt` (+ two actuals)

```kotlin
// commonMain
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
```

```kotlin
// androidMain — delegates to the system back gesture
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) =
    BackHandler(enabled = enabled, onBack = onBack)

// iosMain — no system back gesture; the top-bar arrow handles it
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) { }
```

The canonical `expect`/`actual` example: common code declares *what* it needs, each platform supplies *how*. Notably, this app needs the mechanism exactly once — everything else is genuinely shared.

### `App.kt`

The root composable: navigation, theme and DI wiring.

```kotlin
private sealed interface Screen {
    data object List : Screen
    data class Detail(val productId: Int) : Screen
}
```

A sealed hierarchy makes the destinations exhaustive — the `when` over `screen` can't miss a case, and `Detail` carries its argument in a type-safe way. With two screens this is simpler than a navigation library; a bigger app would swap in `androidx.navigation`.

```kotlin
val listViewModel: ProductListViewModel = viewModel { ProductListViewModel(...) }
```

The list ViewModel is created **at the root**, outside the `when`, so it survives navigation to the detail screen. That's why scroll position, search text and the selected category are all intact when the user comes back. The detail ViewModel uses a per-product `key` so navigating to a different product creates a fresh instance.

`setSingletonImageLoaderFactory` configures Coil once with the Ktor fetcher, giving image loading the same multiplatform HTTP stack as the rest of the app.

---

## Part 5 — Platform entry points

### `androidMain/kotlin/.../MainActivity.kt` + `AndroidManifest.xml`

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
```

Twelve lines: everything the Android app does is call the shared `App()`. The manifest declares `INTERNET` permission and `configChanges` so rotation doesn't recreate the activity (Compose handles it).

### `iosMain/kotlin/.../MainViewController.kt` + SwiftUI host

```kotlin
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
```

Kotlin exposes a `UIViewController`; Swift wraps it in a `UIViewControllerRepresentable`:

```swift
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ vc: UIViewController, context: Context) {}
}
```

`MainViewControllerKt` is the Objective-C class name Kotlin generates for top-level functions in `MainViewController.kt`. The whole Swift footprint of the app is about fifteen lines.

The Xcode project runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` as a build phase, which compiles the Kotlin framework before linking. (`ENABLE_USER_SCRIPT_SANDBOXING = NO` is required for that script to invoke Gradle.)

---

## Part 6 — Tests (`commonTest`, run on JVM **and** iOS)

### `FakeProductRepository.kt`

A hand-written test double implementing `ProductRepository`, with configurable results and call recording:

```kotlin
var searchResult: Result<List<Product>> = Result.success(emptyList())
var getProductsCallCount = 0; private set
val receivedSearchQueries = mutableListOf<String>()
```

No mocking framework needed — one of the practical benefits of depending on an interface. It also works identically on every platform, which most JVM mocking libraries do not.

### `SearchProductsUseCaseTest.kt`

Verifies the three business rules:

```kotlin
@Test
fun nonBlankQueryIsTrimmedAndDelegatedToSearch() = runTest {
    repository.searchResult = Result.success(expected)

    val result = useCase("  phone  ")

    assertEquals(listOf("phone"), repository.receivedSearchQueries)  // trimmed
    assertEquals(0, repository.getProductsCallCount)                 // didn't fall back
    assertEquals(expected, result.getOrNull())
}
```

Plus `blankQueryFallsBackToFullCatalog` and `repositoryFailureIsPropagated`. `runTest` provides a test coroutine scope and skips `delay()` virtually, so the suite runs instantly.

Test names are plain camelCase rather than backtick sentences because Kotlin/Native doesn't allow backticked function names — a small multiplatform gotcha.

### `ProductRepositoryImplTest.kt`

Exercises the real repository against Ktor's `MockEngine` — real parsing, real cache logic, zero network:

```kotlin
private fun successEngine() = MockEngine {
    respond(content = productsJson, status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"))
}
```

Three tests:

- `productsAreFetchedParsedAndMapped` — including that the product **without** a `brand` maps to `null` instead of crashing.
- `secondIdenticalRequestIsServedFromCache` — asserts `engine.requestHistory.size == 1` after two calls, proving the cache actually prevents a network round-trip.
- `httpErrorIsConvertedIntoReadableFailure` — a 500 becomes a `Result.failure` whose message mentions the status code.

Run them with:

```bash
./gradlew :composeApp:testDebugUnitTest        # JVM
./gradlew :composeApp:iosSimulatorArm64Test    # iOS simulator (macOS only)
```

---

## Build & Run

**Prerequisites:** JDK 17+, Android Studio (latest stable); for iOS a Mac with Xcode 15+.

```bash
# Android
./gradlew :composeApp:assembleDebug     # build APK
./gradlew :composeApp:installDebug      # install on device/emulator

# Tests
./gradlew :composeApp:testDebugUnitTest
```

**iOS:** open `iosApp/iosApp.xcodeproj`, select a simulator, Run. For a physical device, set `TEAM_ID` in `iosApp/Configuration/Config.xcconfig`.

## Trade-offs & Assumptions

- **In-memory TTL cache, not SQLDelight/Room** — satisfies the caching bonus with minimal weight; doesn't survive process death. SQLDelight is the natural next step for true offline support.
- **Manual navigation** — two screens; a sealed-class router is simpler and fully shared. `androidx.navigation` (multiplatform) or Decompose would be the upgrade.
- **Search overrides the category filter** — DummyJSON has no "search within category" endpoint, so a query clears the selected category and vice versa.
- **No pagination** — the catalog is fetched with `limit=100`; `limit`/`skip` are already plumbed through the repository, so paging is a small addition.
- **Manual DI** — explicit and readable at this size; Koin would scale better as the graph grows.
- **Readable error strings rather than a typed error hierarchy** — right-sized here; a sealed `DomainError` would be the next refinement.
- **Default launcher icons** — branding was out of scope for a one-day prototype.

## API Endpoints Used

| Feature | Endpoint |
|---|---|
| Product list | `GET /products?limit=100&skip=0` |
| Product detail | `GET /products/{id}` |
| Search | `GET /products/search?q={query}` |
| Categories | `GET /products/categories` |
| Category filter | `GET /products/category/{slug}` |
