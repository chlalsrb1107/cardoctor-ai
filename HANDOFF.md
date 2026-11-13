# 인수인계 — 카닥터 AI v1 (2026-11-13 기준)

다른 PC에서 이 문서 하나만 보고 이어갈 수 있게 정리했다. 자세한 근거는 끝의 "파일 지도"에 있는 문서를 본다.

## 1. 현재 상태

| 항목 | 상태 |
| --- | --- |
| 앱 | Kotlin + Jetpack Compose, Pleos Connect(AAOS) 차량 앱. 기능 구현 완료 |
| 자동 테스트 | **71개 모두 통과** (판정표·일출일몰·예측·이력·음성·경고 정책·정비소 파싱·ViewModel 통합·화면 렌더 12장) |
| release APK | `apk/팀명_카닥터AI_release.apk` (약 76MB, 2026-11-13 09:34 빌드) |
| Pleos 에뮬레이터 실행 | 2026-11-13 새 PC에서 실행 확인. 시나리오 A~D, 브레이크 4등급, 음성 질문 답변, QR 리포트, 예측 카드, 내비 경로 안내까지 동작 |
| 차량 권한 | 매니페스트에 `PRIVATE_CAR_SAFETY`(타이어·경고등), `CAR_DRIVE`(기어) 추가. 없으면 Vehicle SDK가 "need Permission"으로 신호를 하나도 주지 않는다 |
| UI | 2026-11-13 개편: 왼쪽 차량 패널, 오른쪽 판정 카드(긴급도 게이지·판정 근거) + 정비소 카드. 화면이 설계 폭(1408dp)보다 크면 전체를 비례 확대한다(`MainScreen.kt`의 `DESIGN_WIDTH`) |
| 시연 영상 | `docs/demo/cardoctor-ai_demo.mp4` (1분 15초, 새 UI, 장면 자막 포함) |
| 확인 필요 | 앱 음성 경고(TTS)가 에뮬레이터 녹화에서 무음이었다(내비 음성은 녹음됨). Pleos TTS 출력 여부를 확인한다 |
| git | 저장소 초기화·커밋·푸시 모두 안 했다 |

## 2. 새 PC 준비 (권장 사양: 디스크 여유 30GB 이상, RAM 16GB)

1. **Android Studio**를 설치한다. JDK 17이 함께 들어 있다.
2. Pleos 에뮬레이터 이미지를 받는다([공식 가이드](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)).
   - Tools → SDK Manager → SDK Update Sites → **+** 를 누른다.
   - 이름은 `Pleos Connect System Image`, URL은 `https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`(Windows용)을 넣는다.
   - SDK Platforms에서 Show Package Details를 켜고, API 34의 Pleos Connect 이미지를 설치한다. 42dot SDK 라이선스 동의 화면이 나온다.
   - 압축 파일은 3.8GB지만 `system.img`가 풀리면 14GB가 넘는다. 디스크를 넉넉히 확보한다.
3. AVD를 만든다. Device Manager → Create → Automotive → **Pleos Connect v2.1.0**, API 34, RAM 4GB, Default boot는 **Cold**로 둔다.
4. `CarDoctor` 폴더를 통째로 복사하거나 GitHub에 올린 뒤, Android Studio에서 연다.
5. (선택) AI 설명을 켜려면 `local.properties`에 `ANTHROPIC_API_KEY=...` 한 줄을 추가한다. 키가 없으면 기본 안내 문구로 동작한다.
   - `local.properties`의 `sdk.dir`은 이 PC 경로가 들어 있다. 새 PC에서는 Android Studio가 고쳐 주거나, 그 줄을 지우면 된다.

## 3. 실행·시연

```bash
./gradlew installDebug
```

- 앱을 실행하면 위치 권한과 마이크 권한을 요청한다. 둘 다 허용한다.
- **에뮬레이터 GPS를 판교로 맞춘다.** 에뮬레이터 기본 GPS는 미국(마운틴뷰)인데 Pleos 내비 엔진은 판교 기준이라, 그대로 두면 예시 정비소가 미국에 찍혀 "내비로 안내"가 `CAUSE_SEARCH_ROUTE`(경로 탐색 오류)로 실패한다. 에뮬레이터를 켤 때마다 아래를 실행하고 앱을 다시 시작한다.

  ```bash
  adb emu geo fix 127.094681 37.4127212
  ```
- 에뮬레이터는 타이어 공기압·엔진오일·워셔액·브레이크액 신호를 제공하지 않는다(`property ID is not in carPropertyConfig list`). 이 항목은 데모 패널로 시연한다.
- **데모 패널:** 좌상단 "카닥터 AI" 제목을 길게 누르면 열린다.
  - 시나리오 A 낮·전조등 → 1등급
  - 시나리오 B 밤·전조등 → 3등급, 음성 경고
  - 시나리오 C 빗길 고속 타이어 → 3등급. "정차하기"를 누르면 상세 화면이 나온다
  - 시나리오 D 서서히 빠지는 공기압 → 예측 정비
  - 음성 질문 대신 문장을 넣는 버튼(마이크가 없는 환경용)
  - 고장 주입, 시간·날씨·속도 덮어쓰기
- **음성 질문:** 상단 "음성 질문" 버튼. "지금 가야 돼?", "왜 위험해?", "정비소 안내해줘", "리포트 보여줘"
- 영상 촬영 순서는 `docs/DEMO_SCRIPT.md`를 따른다(2분 40초, 8장면).

**에뮬레이터에서 꼭 확인할 것** (지금까지는 에뮬레이터 없이 단위·렌더·통합 테스트로만 검증했다)

- [ ] 상단 칩에서 "차량 SDK 미연결"이 사라지는지(Vehicle Service 연결)
- [ ] 버추얼 콘솔에서 경고등(TPMS 등)·타이어 공기압·기어(P↔D)를 바꾸면 화면 등급이 바뀌는지
- [ ] "내비로 안내"를 누르면 차량 내비에 목적지가 들어가는지(NaviHelper)
- [ ] 3등급 이상에서 음성 경고가 나오는지(TTS), 음성 질문이 인식되는지(STT)
- [ ] 위치가 FusedLocation에서 들어오는지, 정비소 목록 제목이 "OpenStreetMap"으로 뜨는지
- [ ] 문제가 생기면 Logcat에서 `VehicleSource`, `NaviSource`, `Speaker`, `Listener`, `EnvironmentSource`, `ShopSearch` 태그를 본다

## 4. 테스트·빌드

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

- 프로젝트 경로에 **한글이 있는 Windows**에서는 테스트가 `ClassNotFoundException`으로 실패한다. 빌드 폴더를 ASCII 경로로 옮겨서 실행한다.

  ```bash
  ./gradlew -PbuildRoot=C:/build/cardoctor :app:testDebugUnitTest
  ```

  Kotlin 데몬이 한글 경로를 깨뜨리는 문제는 `gradle.properties`의 `kotlin.compiler.execution.strategy=in-process`로 이미 막아 두었다.
- 화면 렌더 결과는 `docs/renders/`에 PNG로 저장된다(`ScreenRenderTest`).

## 5. 제출 전에 반드시 할 일 (마감 11/15 일 23:59)

`docs/SUBMISSION_CHECKLIST.md`에 전체 목록이 있다. 핵심만 적는다.

1. **긴급출동 번호 확인:** `MainViewModel.ROADSIDE_NUMBER = "080-600-6000"`은 **검증하지 않은 값**이다. 공식 번호로 바꾼다.
2. APK 파일명의 `팀명`을 실제 팀명으로 바꾼다(`apk/팀명_앱이름_release.apk` 규칙).
3. 접수양식(DOCX)을 받아 `docs/PROPOSAL_DRAFT.md` 내용을 옮긴다. `[확인 필요]` 표시는 채우거나 지운다.
4. 팀원 전원 서명 동의서를 받는다. 메일 제목은 `[차량용 앱 개발 공모전] 팀명_대표자명`, 팀원 전원 CC, 본문에 GitHub URL과 최종 커밋 해시를 넣는다.
5. GitHub에 올리고 **11/16(월)까지 Public으로 전환**한다. `local.properties`(API 키)는 `.gitignore`에 들어 있다.

## 6. 팀이 정해야 할 것 (구현하지 않고 남김)

- 반복 고장(30일 중 3일 이상)이면 등급을 한 단계 올릴지. 지금은 칩으로 표시만 한다.
- R8 코드 축소로 APK(76MB)를 줄일지. 에뮬레이터에서 동작을 확인한 뒤에만 켜는 게 안전하다.
- API 키를 APK에 넣는 방식은 데모용이다. 배포라면 서버 프록시로 옮긴다.
- 정비소 데이터(OpenStreetMap)는 국내 수록률이 지역마다 다르다. 국내 지도 API나 제휴 정비망 데이터로 보강할지.

## 7. 판정 규칙 요약

긴급도 = 기본 심각도(1~3) + 환경 가중치. 6 이상이면 4등급(즉시 정차), 4~5는 3등급(즉시 정비), 2~3은 2등급(목적지 후), 1은 1등급(여유)이다. 기본 심각도 3인 부품(브레이크·냉각·조향 등)은 항상 3등급 이상이다.

| 환경 | 대상 | 가중치 |
| --- | --- | --- |
| 야간 | 등화 | +3 |
| 비·눈 | 와이퍼·워셔액 | +3 |
| 비·눈 | 제동 | +3 (PRD 초안 +2에서 조정) |
| 비·눈 | 타이어 | +2 |
| 고속(80km/h 이상) | 타이어·제동·조향 | +1 |
| 목적지 50km 이상 | 배터리 | +2 (PRD 초안 +1에서 조정) |
| 목적지 50km 이상 | 냉각·엔진 | +1 |

등급은 `engine/RuleEngine.kt`만 정한다. AI(Claude)는 설명 문장만 쓰고 등급을 바꾸지 못한다.

## 8. 파일 지도

| 경로 | 내용 |
| --- | --- |
| `README.md` | 앱 소개, 실행 방법, 테스트 환경, 구조, 제약 (GitHub 제출용) |
| `HANDOFF.md` | 이 문서 |
| `apk/` | release APK |
| `docs/SUBMISSION_CHECKLIST.md` | 1차 제출 체크리스트 |
| `docs/PROPOSAL_DRAFT.md` | 기획서 본문 초안 (14장 + 사업성) |
| `docs/DEMO_SCRIPT.md` | 시연 영상 대본 |
| `docs/PT_QA.md` | PT 예상 질문과 답변 |
| `docs/NIGHT_LOG.md` | 11/13 밤사이 작업 기록 (무엇을 왜 바꿨는지) |
| `docs/renders/` | 화면 렌더 12장 |
| `app/src/main/.../engine/` | 판정 엔진, 일출일몰, 예측, 리포트, 음성 명령, 경고 정책 (순수 Kotlin) |
| `app/src/main/.../sdk/` | Pleos SDK 연동: Vehicle, NaviHelper, TTS, STT, 경고등 매핑 |
| `app/src/main/.../data/` | 모델, 위치·날씨(FusedLocation), 정비소(OSM), 이력, 데모 |
| `app/src/main/.../ui/` | 화면, ViewModel |
| `app/src/test/` | 자동 테스트 71개 |

팀 공통 PRD 문서: https://claude.ai/artifact/MgAsHPhmBfu9jCMwQfbMMj
