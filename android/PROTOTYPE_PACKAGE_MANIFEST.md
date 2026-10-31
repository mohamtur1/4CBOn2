# 42 — Package Prototype and Support Matrix

**Date:** 31 October 2026  
**Branch:** `arena/61ac1ccb-4cbon2`  
**Status:** Source checkpoint complete. Build verification requires the Android toolchain (JDK 17 + Gradle + Android SDK) on a machine with access to `google()`/`mavenCentral()`; this sandbox cannot compile or download the toolchain (outbound network limited to GitHub/npm/PyPI).

## Rebuild instructions

1. Requires JDK 17, Android SDK (compileSdk 37), Gradle wrapper included.
2. `cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug`
3. The retained Division 05 APK (`4CBOn2-debug.apk`) **predates Divisions 06–42** and must not be shipped as the current build.
4. Release signing remains under the owner's control; no release key is bundled.

## Source checksums (SHA-256)

### Main sources (49 files)

| File | SHA-256 |
|---|---|
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/AndroidKeyStoreCredentialStore.kt` | `bc776fd86e81b12d758d6d320a69726bc82a9c6bb76b507af7d8581532bf679e` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/AppDestination.kt` | `7c563e981c774b6dc083bd8d8bad212b8dcdd19981ca133ff0e7af776e62b06e` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/ColabNotebookReference.kt` | `2ce05ce11eba9f41a5c73371fd8a1487f2a011c0fef9058bb16b5c32b2975cb4` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/CredentialSlot.kt` | `91be2a2cf0b8dd0640a93499524f21c3240e6b16b610a98c80bd8ed90b18ecee` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/CredentialStore.kt` | `aaa0dd18d166a30a334f10a0651fe4a73fae388dc12c3fcb3a29167f9cf44943` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/EncryptedCredentialRecord.kt` | `7634d82a601b13305132564c50109760d54ad7c449e3736bf23ac29da940398b` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/GitHubReadResult.kt` | `3a03fe7c7774d72e457ab44aa2c29553bea032e4f1a0465589063cc343c78b42` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/GitHubRepositoryId.kt` | `c836f1034c8ff16644333e17d3b4bac8cd89065b14c0f5c732a63d1086865309` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/GitHubRepositoryReadClient.kt` | `64e9bd6a4a04c0113802622341008b69c6e1032c571b87c1e8cf40769c102dfb` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/KaggleCompetitionEndpoint.kt` | `c5671c72cc65b2b363858f178f472b8803bbb907718e31a2a4411b25a39096d6` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/KaggleCompetitionReadClient.kt` | `e17b9b80a6b66179ba62a68dd3cc49b052df219d309e18dcc99430cab10e7408` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/KaggleReadResult.kt` | `fbfa37939a106f31c2efa475959eb21434df12206e982ae0d5574392cd2de154` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/MainActivity.kt` | `45636ecd632030f75c48a0dad19b74a79f79f88f9cbeded91684b21c132423b7` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/JobQueue.kt` | `a02fce19be031f98195fe1c2713d61cbcfe0a3c0f90c2f72a304e7a2b493eed3` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/JobRecordCodec.kt` | `65f4aa775879feda61080a28c53eabb66d90ed8bd74229673f526e79e9def73b` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/JobState.kt` | `13eaece8a32c8d553611056bc1171b4af4a533cc9557df2e68b369e92395fd00` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/LocalDocumentStore.kt` | `d766114bda3dadb64fd82619cb438eb2a6d1e2a838bb9b066367cb4f44b5d3ec` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/LocalStoreModels.kt` | `e3c3ff672b7752855ad10990aacafe7e3dc7f2d5035c6e3c0bf2a2f9c79b34e6` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/local/RecordCodec.kt` | `e078eca58b90c138ce35988be103e777b8ec9e76d69c9875c7fcfbc13d1ea4b6` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/capability/AppCapability.kt` | `669cfa94371781e762deb422345038e26d27f264f81c5c3a34f672a55d6641f7` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/capability/AppCapabilityRegistry.kt` | `50f3c19b198fa5deb2c84da2f1576411ac9ea6becfe6dd36c88aa8b7c22cca53` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/capability/SelectedApp.kt` | `49300bec09d09467ab8b9db370160a2b60754ba998a05f617ee0766f568e96bb` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/scope/ScopeContract.kt` | `5e7700baaaa450794bb61ebd8ee2477a2823bbc8ad18d11f819d692591004e01` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/control/TakeoverLease.kt` | `e8a8845768d13bb29dd9d2307047d72f77e3881346b80d8137071531e7b6a802` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/agents/AgentDraftEngine.kt` | `c6d40fd9daaa355b7545c8b08cb7b36322702f4b84cb87f957856191f76ed9a7` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/files/DocumentWorkspace.kt` | `5887e3265a9dd32c251de9faab448572411f728abda6dded65abb7fe7af62340` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/knowledge/KnowledgeLibrary.kt` | `1c4cc16cb64099c77bb600e8df7b18483ab67b1017664048dc07f94308ff6705` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/analysis/LocalAnalysis.kt` | `06076a4f1449a41044286f5a3002596a461c660fa4793689959c8df905280e42` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/approval/ApprovalGate.kt` | `94853c76f5ce68854cf05eebeb4dc693fa837fbaa0c204485a0bc3d242c9fc01` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/action/IntentActionRunner.kt` | `5a1b473b5b47c26235b5bd3fe8409002b1b9bc0259ef211267c29072f53154b4` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/action/SupportedAppAction.kt` | `27a0fa8f9fa6033202ce048d9c6b1349ec568d5e74a39dc605c301a2069c69b3` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/github/GitHubCacheStore.kt` | `bf34447b46f9775b392e12488a8108ba44b5bf53e9892db3ee93a173a27a096c` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/github/VersionDiff.kt` | `3ad37c2b45a5ef04fdcad53c8abcf95c3f2c012e5b886b619ce16e55067f5f58` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/kaggle/KaggleStatusAdapter.kt` | `ba4ab5d96fa9d696fff823299a1030c53f1aca3b2fe447150a1beba4778fa381` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/browser/BrowserWorkspacePolicy.kt` | `6d7cfd391165e808f0c08f2ea527007f7a858d265ece0845c4495aedaff7ca91` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/browser/CustomTabOpener.kt` | `b57e1be2968fd76f54945ea1ac742ff8d8c06c2419c19d1517d43af20343b28d` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/observation/ObservationConsent.kt` | `bc0554c7c66bd9cdfa3dba030c0066fab2b22a8f9ed0bfb91e79441c3d3da3dc` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/timeline/ActionTimeline.kt` | `641542fda2becef7496961b3ef190bfe61a3c312edad55b69fdced2a26633b06` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/bundle/JobBundle.kt` | `0bf65dcd02931e1df15c7bfead7419f1a1ba5891afec8b8738415b7eff710600` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/notebook/NotebookContract.kt` | `0b9bc8158b76fa03fe6c37c0353f4dc031407f3a0a5d2a3287aff587810d63fc` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/ai/ColabAiAdapter.kt` | `32b21e6a0c54f064360d52fa0faa9148dddf952821926c3189cd3507bd4ab9ff` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/agent/BoundedAgentLoop.kt` | `d37145b52bc3771d99abbdbc3367d42aa4f1d09cb7fbb95ec767cadfdf83a17d` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/guard/CriticalActionGuard.kt` | `ede26f89542dcbcc29464c113e8377c5e8709b8a93661bad7c4ef871a2ce7061` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/routines/RoutineEngine.kt` | `b5e9afe3764cc480d048939e2ee3cb2f9431257e2fd7f6343f8735256af2d149` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/lab/ImprovementLab.kt` | `5bba4a1355218026bdd83e3b8991b498e2fc4bfdf31cce2aa51c3a7a0473321c` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/lab/LabRunnerEligibility.kt` | `756b7129e6748fdba1fd14729ff7cf947617577037d6796b6bec4c78a888bd62` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/routing/ControlRoutingPolicy.kt` | `f0646262d9a4f4e6d9e9b65fc3c8b47585e4eea9ed1a4927fffcc99af2a98230` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/pipeline/PrepareOnlyPipeline.kt` | `6f9afc5a33aebe27383065a44255f041e52047ee820ae8872cb15d173efe71b9` |
| `android/app/src/main/java/io/github/mohamtur1/fourcbon2/submission/SubmissionGate.kt` | `1581acd96190541a2e6b3753b114c45489c3d136866e64eef055f3909f8889f3` |

### Unit tests (42 files)

| File | SHA-256 |
|---|---|
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/AppDestinationTest.kt` | `ff4f040b2329d3cfb488bd08bdf9a02e980f4202344726b16e231b6a17137f8f` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/ColabNotebookReferenceTest.kt` | `829fb8ebe36ba89928c833aee4a6e85af96be6f17fb6a01c5911553f29d414ef` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/CredentialSlotTest.kt` | `320ef615da00ad812dcf1ff4159c2d9db5f3c1861fbe8afff34560687d3cd27e` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/EncryptedCredentialRecordTest.kt` | `37276d50e24f9bb34e99a9a7959f844775c662763ce40b8b86931f5326ad2351` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/GitHubReadResultTest.kt` | `e5c3566027377e0146f99e6388a12bed6da9532427bf437860f2d5a0d007f581` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/GitHubRepositoryIdTest.kt` | `2921935a5fbcda6635b39983c01b90bc61f456e810cdc5c1051c2615a311f5ff` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/GitHubRepositoryReadClientTest.kt` | `7d937184d2437320aac882175d9acf7455c437fce84107de5c127d5ce2f8a915` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/KaggleCompetitionEndpointTest.kt` | `8284fe79346a5f2e2b9fda9342816e7d0a9635a9451ce3684c6be7823b6cebf8` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/KaggleCompetitionReadClientTest.kt` | `1c3c9b9a2b074d8f0a2b4a49d343c34003bfb49c89b598deacb4bfc722f73fa9` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/KaggleReadResultTest.kt` | `e4104d4a81c6a7dba4e38f818b15abd74de6f39629789b2106e307d5a5165eb5` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/local/JobQueueTest.kt` | `3241cdf9558246c5dd5882d63b58491c9003f110024e29e4ad333d11ba736791` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/local/LocalDocumentStoreTest.kt` | `0f319caa69a75fa6b82c66d3b5398066ec572a586e314eb91f82cb9098423765` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/capability/AppCapabilityRegistryTest.kt` | `4f031a77f6e647e17bd07f1f6074addce8b344b304174051204ed1cb45b71fe6` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/scope/ScopeContractTest.kt` | `4dd94268f81c0da13abb5545c215f142a0d779acc4e1d33cedfb1deee05daac1` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/control/TakeoverLeaseTest.kt` | `2bf2297b926f22b00ce230b994dc4cb62119415278ad7a8fa72fc9b1cfde2b62` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/agents/AgentDraftEngineTest.kt` | `104e3d77db18a2c6129e21dd726f6d2bd415c15904aa205e85a37b74606f7f02` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/files/DocumentWorkspaceTest.kt` | `f99392a7fc7fa9d4773f2453362a0a2d5dc89b838857c0fa02c8c9d2b8ad55dc` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/knowledge/KnowledgeLibraryTest.kt` | `0914562914b1500e35dc3979b3c82eecdb15354cdd50735de4e8103608218c40` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/analysis/LocalAnalysisTest.kt` | `cdec0de578d54576acf3de1dbf9407e67782a2b15bb9b69e5485ce8bd054272d` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/approval/ApprovalGateTest.kt` | `68652bc7e8f6c16b143fb244ad2e30cbdd075cd4720afa458729806d92978c43` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/action/IntentActionRunnerTest.kt` | `04f68c18924208bb20a785e1fc51caf3f620dadbb13ff45345d907e217ea2b19` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/github/GitHubCacheStoreTest.kt` | `c111d6480019a396e397a30fdaee4160f046558bd117815fa049388e30c382d0` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/github/VersionDiffTest.kt` | `5cbb5af7390862bd01c011ef21437af5d4aa6eeda91949d48a6a79819ce157d4` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/kaggle/KaggleStatusAdapterTest.kt` | `a160373d96bd30a8b714620e5f1634f621fa2b42dae51b7dbe132833b9912fea` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/browser/BrowserWorkspacePolicyTest.kt` | `262c85a8148ed9535586f2036e03094702826adeb487c392650200820d78237b` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/browser/CustomTabOpenerTest.kt` | `bf17926e42485a47e152f9174debe18ac646e22098da07e71880a292dcce70ae` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/observation/ObservationConsentTest.kt` | `ad364a1267eaaac4ac6dff4a2fd94ffa766b14c03d9a1542bc367a344b8dfc54` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/timeline/ActionTimelineTest.kt` | `53590447340648f1be1d9c1dc6eaecf2fe712c898e1a10aa3c54bfd5d18ea684` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/bundle/JobBundleTest.kt` | `2fa594779ab0b8a621dafc477571dc882a76113a08d7fcdc113c36b9c55be9b1` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/notebook/NotebookContractTest.kt` | `d1a07ebb7482272727a7ddbfc5761d067fcd1ceda6ae0b67f42f944b7c24cee9` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/ai/ColabAiAdapterTest.kt` | `85d125d6ce18d4e0e4d564344012cc5e30bba2076b31ea21d849e114274bd246` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/agent/BoundedAgentLoopTest.kt` | `52ae0253edd57acac32db9f0dba4dbbdf64e6dfb90847132b9faf052ebab0878` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/guard/CriticalActionGuardTest.kt` | `cdf77d4c5613859e6aeab1d29c7189e50b3c397871ba4d22808b7aa6d99d0c4f` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/routines/RoutineEngineTest.kt` | `249486f78c49e72dc248d9c01a72fed0bf3f6fffd73c81ef9de8a261a4b2bed9` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/lab/ImprovementLabTest.kt` | `e5731f8eba8b6ce6a85401a469279382bcb2c7d5206e61a71e1b6518ca55dbc0` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/lab/LabRunnerEligibilityTest.kt` | `50d5615ec7c053a721327119eab929050176ecca5c03e8e42f7ba7cf4e33817f` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/routing/ControlRoutingPolicyTest.kt` | `47741cd4996604e21d11f3a4b2f87ee9e8c16ac58d3840f95a5f8e2daff38265` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/safety/PrivacyAndInjectionTest.kt` | `68ed3c81f27abe9ad7843b7d8f97c36d3f3191ddb0500a52d438012f42bc5c2f` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/safety/RecoverySmokeTest.kt` | `aadaf5a8f503a9eb926baaf3c3555f70ad4a78b92cc1d4ebe470ddf94fb64145` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/pipeline/PrepareOnlyPipelineTest.kt` | `3911bd377aab2e971adff9e7d6f3930484c89f9cbc575a288d65f12bfde4bdb5` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/submission/SubmissionGateTest.kt` | `78c37fe2cb1b4fa753e3cf411151d33633a290ba0e8c1bd989db2d9ea638d1ee` |
| `android/app/src/test/java/io/github/mohamtur1/fourcbon2/integration/CrossAppIntegrationTest.kt` | `9b0d927fb5cf6e884387ae5378e199f583c483df8a5fcbcf95bb9cb824c1fdb7` |

## Test coverage summary

- **Main sources:** 49 files (Divisions 09a, 11–42).
- **Unit tests:** 42 files, ~140 tests (JUnit 4, pure-JVM).
- Verification status: **static/source + unit-test-written**; not build-verified in this sandbox.

## Blocked capabilities (honest support matrix)

| Capability | State | Reason |
|---|---|---|
| Live Kaggle read/submission | Blocked | Requires owner-authorized Kaggle credential (Divisions 09/23/37/38); no credential entered or requested |
| Live Colab built-in AI | Blocked | Requires an actual Colab runtime + authorized account (Division 30); adapter reports the gap honestly |
| Device/emulator install & test | Blocked | No device/emulator in this environment (Divisions 05/34/42) |
| Play Store / production release | Blocked | Not attempted; no release key; store eligibility unproven |
| Accessibility-service takeover / universal phone control | Not implemented | Excluded by design (Division 34 boundary) |
| Release APK rebuild | Not performed | Toolchain unavailable in this sandbox |

## Distribution limitations

- The APK is a development build; no store release or production signing is claimed.
- Divisions 11–42 are source + test checkpoints; they must be build-verified before any device use.
