# Tasks — Backend · Catalog context (phase 2)

Browsing and filtering published exercises. **[Backend]** + the catalog
page. Approved design: backend design §8b (read-only context, no domain
layer).

---

- [x] 1. Catalog API + catalog page
  - Backend: `com.bandup.api.catalog` — `BrowseExercises` (query + filter
    choices), `CatalogQuery`, `CatalogPage`, `ExerciseCard`,
    `CatalogFilters`, port `ExerciseCatalog`, `JdbcExerciseCatalog`,
    `CatalogAccessRules`, `CatalogController` (`GET /api/v1/exercises`,
    `GET /api/v1/exercises/filters`).
  - Frontend: `/reading` is the catalog (`CatalogPageController`,
    `catalog.html`, `catalog.css`, `catalog.js`): level / type / topic chips,
    length, time, "not done yet" (signed in), cards with level, length,
    time, questions, types, topics and "✓ Done · best x/y", pages; filters
    kept in the URL. Navbar "Reading" and the home page link here.
  - _Verified_: `CatalogApiTest` (7, MockMvc): cards with every field and no
    answers, az / en topic names; drafts and archived hidden; every filter
    alone and combined (AND across, OR within; untimed always fits
    `maxMinutes`); paging newest first; signed-in done / best of two
    attempts / `notDone`, ignored when anonymous; every malformed filter in
    one 400; filter choices only from published exercises. Mutation check:
    without the PUBLISHED condition the drafts test fails.
    `CatalogBrowserTest` (3, Chrome): navbar → catalog → card → passage;
    chips / time / clear update the list and the URL, a URL with filters
    restores them, no-match message; signed-in learner sees "✓ Done · best
    1/6" and can hide it. Selenium scenario
    `LearnerScenariosTest.browseTheCatalog` (watch with `sh gradlew uiTest`).
    `UiPagesTest` covers `/reading`.
  - _Requirements: 9; database 7_
