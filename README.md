# 카닥터 데모 (Pleos Connect)

차량 경고등을 읽어 **"지금 정비소에 가야 하는지"** 를 4단계로 알려주는 데모 앱입니다.
Pleos Vehicle SDK 2.3.2로 신호를 읽고, 규칙 기반 엔진이 고장의 기본 심각도와 주행 환경을 합쳐 등급을 정합니다.

| 낮, 전조등 이상 → 1등급 | 밤, 같은 고장 → 3등급 |
|---|---|
| ![낮](docs/screenshots/A_headlamp_day_grade1.png) | ![밤](docs/screenshots/B_headlamp_night_grade3.png) |

| 비 오는 날 타이어 공기압 → 3등급 | 브레이크 + ABS → 4등급 |
|---|---|
| ![타이어](docs/screenshots/C_tire_rain_grade3.png) | ![브레이크](docs/screenshots/D_brake_abs_grade4.png) |

## 목차

- [빠른 시작](#빠른-시작)
- [앱 사용 방법](#앱-사용-방법)
- [고장 주입 방법](#고장-주입-방법)
- [시연 순서 예시](#시연-순서-예시)
- [시연 영상 녹화](#시연-영상-녹화)
- [동작 방식](#동작-방식)
- [프로젝트 구조](#프로젝트-구조)
- [디자인](#디자인)
- [크레딧과 라이선스](#크레딧과-라이선스)
- [알려진 제약](#알려진-제약)
- [운전 중 안전](#운전-중-안전-화면-잠금--음성)
- [시연 안정성](#시연-안정성)
- [보안 (API 키)](#보안-api-키)
- [통합 기능 (회의 PRD F1~F7)](#통합-기능-회의-prd-f1f7)
- [신호 매핑표](docs/SIGNAL_MAP.md)

## 빠른 시작

### 준비물

| 항목 | 버전 | 비고 |
|---|---|---|
| JDK | 17 | `JAVA_HOME` 이 JDK 17을 가리켜야 합니다 |
| Android SDK | Platform 35, Build-Tools 35 | `local.properties` 에 `sdk.dir=<SDK 경로>` 를 적거나 `ANDROID_HOME` 을 설정합니다 |
| Pleos Connect 에뮬레이터 | v2.1 (Android 14) | 일반 Android 기기에는 Pleos Vehicle Service가 없어 차량 신호를 읽을 수 없습니다 |
| Python | 3.9 이상 | GUI 시연 콘솔을 쓸 때만 필요합니다. 추가 패키지는 필요 없습니다 |

### 1. 에뮬레이터 실행

Pleos Connect AVD를 **콜드 부팅**으로 띄웁니다. 빠른 부팅(스냅샷)으로 띄우면 차량 신호가 동작하지 않습니다.

```
emulator -avd Pleos_Connect_v2_1 -no-snapshot
```

`emulator` 는 Android SDK의 `emulator` 폴더에 있습니다. AVD 이름은 `emulator -list-avds` 로 확인합니다.

### 2. 빌드와 설치

저장소 폴더에서 실행합니다. Windows `cmd` 에서는 `./gradlew` 대신 `gradlew`, PowerShell 에서는 `.\gradlew` 를 씁니다.

```bash
./gradlew installDebug
```

### 3. 앱 실행

에뮬레이터의 앱 목록에서 **카닥터**를 누르거나 아래 명령을 실행합니다.

```bash
adb shell am start -n com.cardoctor.demo/.MainActivity
```

화면 아래에 "Pleos Vehicle SDK 연결됨"이 보이고 종합 판정이 "정상"이면 준비가 끝난 것입니다.

가까운 정비소 검색과 길안내까지 쓰려면 에뮬레이터 GPS를 국내 위치로 맞춰야 합니다. [에뮬레이터 준비](#에뮬레이터-준비-f3f4f7)를 참고하세요.

### 4. 테스트 (선택)

```bash
./gradlew testDebugUnitTest
```

판정 엔진·음성 해석의 단위 테스트 29개를 실행합니다. 에뮬레이터 없이도 돌아갑니다.

## 앱 사용 방법

![정상 화면](docs/screenshots/0_normal.png)

| 영역 | 내용 |
|---|---|
| 왼쪽 **VEHICLE STATUS** | 3D 차량. 천천히 자동 회전하고, 드래그하면 직접 돌려 볼 수 있습니다. 고장이 감지되면 그 부위가 등급 색으로 빛나고 카메라가 그쪽으로 돌아갑니다 |
| 오른쪽 위 **OVERALL** | 종합 등급과 지금 할 일. 다이얼이 등급만큼 채워지고 4등급은 점멸합니다 |
| 오른쪽 아래 **고장 카드** | 고장마다 등급, 판정 근거(기본 심각도 + 환경 가중치 = 점수), 설명, 할 일 |
| 위쪽 **주행 환경 칩** | 판정에 반영되는 환경. 눌러서 바꿀 수 있습니다 |
| 맨 아래 | 차량 서비스 연결 상태 |

### 주행 환경 칩

같은 고장도 환경에 따라 등급이 달라집니다. 칩을 눌러 환경을 바꾸면 등급이 바로 다시 계산됩니다.

| 칩 | 누를 때마다 | 기본 동작 |
|---|---|---|
| 낮 / 밤 | 자동 → 밤 강제 → 낮 강제 → 자동 | 시스템 시각 18~06시를 밤으로 봅니다 |
| 맑음 / 비·눈 | 자동 → 비·눈 강제 → 맑음 강제 → 자동 | 앞 와이퍼가 작동 중이면 비로 봅니다 |
| 일반도로 / 고속도로 | 번갈아 전환 | 수동 |
| 근거리 / 50km 이상 | 번갈아 전환 | 수동 |

강제로 바꾼 칩에는 주황색 점과 "강제"가 표시됩니다.

### 등급

| 등급 | 점수 | 의미 | 안내 |
|---|---|---|---|
| 4 | 6점 이상 | 즉시 정차 | 안전한 곳에 정차하고 견인·긴급출동을 부르세요 |
| 3 | 4~5점 | 즉시 정비 | 가장 가까운 정비소로 바로 가세요 |
| 2 | 2~3점 | 목적지 후 정비 | 목적지까지는 운행할 수 있습니다. 오늘 중 점검을 권장합니다 |
| 1 | 1점 | 여유 있게 정비 | 주말이나 다음 정기점검 때 정비소를 방문하세요 |

## 고장 주입 방법

실제 고장을 낼 수 없으므로 PC에서 고장 신호를 넣습니다. **앱에는 시연용 화면이 없습니다.** 시연 중에도 앱은 실차에서와 같은 화면만 보여 줍니다.

- 경고등과 와이퍼는 에뮬레이터의 Virtual Console(기기 포트 8088)에 넣습니다. 앱은 SDK로 읽힌 값에만 반응합니다.
- 차량 신호가 아닌 앱 안의 값(바퀴별 타이어 시연값, 주행 환경, 음성 질문 예시)은 adb 브로드캐스트로 앱에 보냅니다.
  받는 쪽(`demo/DemoCommands.kt`의 `DemoCommandReceiver`)은 `android.permission.DUMP`로 막혀 있어 adb 셸만 보낼 수 있고, 차 안의 다른 앱은 보낼 수 없습니다.

| 방법 | 쓰기 좋은 때 |
|---|---|
| ① GUI 시연 콘솔 (PC) | 시연 전체. 고장 · 타이어 · 주행 환경 · 음성 질문을 한 화면에서 조작하고 앱의 판정까지 로그로 봅니다 |
| ② 명령줄 스크립트 | 정해진 경고등 시나리오를 빠르게 넣을 때 |

### ① GUI 시연 콘솔 (PC)

![GUI 시연 콘솔](docs/screenshots/F_demo_console_tool.png)

```
python tools/demo_console.py
```

에뮬레이터가 켜져 있으면 자동으로 연결됩니다. `adb` 는 PATH, 환경 변수 `ADB`, Android SDK 기본 위치 순으로 찾습니다.
(Claude 데스크톱 앱 안에서 SDK를 설치했다면 SDK가 `%LOCALAPPDATA%\Packages\Claude_*\LocalCache\Local\Android\Sdk` 로 가상화되는데, 이 위치도 찾습니다.)

세로(9:16) 창입니다. 위쪽 탭 세 개(**시나리오**, **고장 주입**, **앱·음성**)로 조작하고, 아래 **VEHICLE STATE** 와 **LIVE LOG** 는 탭과 관계없이 항상 보입니다.

| 영역 | 내용 |
|---|---|
| **SCENARIOS** | 누르면 기존 고장을 지우고 시나리오를 주입합니다. 오른쪽 링의 숫자는 앱에서 기대되는 등급입니다 |
| **FAULT INJECTION** | 경고등 고장과 와이퍼를 하나씩 켜고 끕니다. 불이 켜진 항목은 차량이 실제로 보고하는 상태입니다 |
| **OTHER TELLTALES** | 목록에 없는 경고등을 종류·색·상태(On / Blink / Off)를 골라 넣습니다 |
| **VEHICLE STATE** | 지금 켜져 있는 경고등과 앞 와이퍼 상태 |
| **APP · TIRE PRESSURE** | 바퀴별 저압 시연값(120kPa). v2.1 에뮬레이터에는 실측 공기압이 없어서 앱 안에서 만듭니다. 앱 화면과 리포트에 "시연값"으로 표시됩니다 |
| **APP · DRIVING CONTEXT** | 주행/정차, 밤/낮, 비·눈/맑음, 고속도로/일반도로, 50km+/근거리 모두 누를 때마다 자동 → 강제 ON → 강제 OFF. **시연 환경 고정**은 낮·맑음으로 고정해 실시간 날씨나 시각 때문에 등급이 바뀌지 않게 합니다 |
| **APP · VOICE** | 음성 질문 4개. 에뮬레이터에는 마이크 입력이 없어서 버튼으로 보내며, 앱은 실제 음성과 같은 경로로 화면과 음성으로 답합니다 |
| **LIVE LOG** | 도구가 보낸 요청, 차량이 보고한 변화, 앱의 판정(`[앱]`, logcat 태그 `CarDoctorLog`)을 시간순으로 |
| **앱 실행 / 화면 캡처** | 캡처는 `captures/` 폴더에 시각이 붙은 PNG로 저장됩니다 |

"모두 해제"는 경고등 · 와이퍼와 함께 타이어 시연값도 지웁니다.

**시연 환경 고정**을 누른 직후에는 밤/낮 · 비·눈/맑음 버튼의 첫 클릭이 "자동"으로 돌아갑니다(고정 → 자동 → 강제 ON → 강제 OFF). 밤 시간대에 시연하면 자동도 밤으로 판단하므로, 낮으로 되돌리려면 두 번 더 눌러 강제 OFF로 맞춥니다.

명령을 직접 보낼 수도 있습니다.

```
adb shell am broadcast --user current -n com.cardoctor.demo/.demo.DemoCommandReceiver -a com.cardoctor.demo.DEMO --es cmd tire --es wheel RIGHT_REAR --ez low true
```

| cmd | 추가 값 |
|---|---|
| `tire` | `wheel` = LEFT_FRONT · RIGHT_FRONT · LEFT_REAR · RIGHT_REAR, `low` = true/false |
| `tires_clear` | 없음 |
| `env` | `key` = driving · night · weather · highway · long, `value` = auto · on · off |
| `env_lock` / `env_reset` | 없음 (낮·맑음 고정 / 모두 자동) |
| `ask` | `q` = 0~3 (지금 정비소 가야 돼? · 왜 위험해? · 정비소 안내해줘 · 리포트 보여줘) |

### ② 명령줄 스크립트

Windows `cmd` · PowerShell 에서는 `scripts\demo.cmd`, Git Bash · macOS · Linux 에서는 `scripts/demo.sh` 를 씁니다. 인자는 같습니다.

```
scripts\demo.cmd headlamp
```

| 인자 | 주입 내용 | 앱에서의 결과 |
|---|---|---|
| `headlamp` | 전조등(LED 헤드램프) 경고등 | 낮 1등급. 앱에서 밤으로 바꾸면 3등급 |
| `tire-rain` | 타이어 공기압 경고등 + 와이퍼 작동 | 3등급 |
| `brake` | 브레이크 경고등 + ABS | 4등급 |
| `overheat` | 과열 경고등 | 3등급 |
| `battery` | 저전압 배터리 경고등 | 3등급 |
| `steering` | 조향 보조(MDPS) 경고등 | 3등급 |
| `airbag` | 에어백 경고등 | 2등급 |
| `clear` | 모두 해제 | 정상 |

인자 없이 실행하면 사용법이 나옵니다. `demo.sh` 에서 `adb` 가 PATH에 없으면 `ADB=<adb 경로> scripts/demo.sh headlamp` 처럼 지정합니다.

## 시연 순서 예시

"같은 고장도 상황에 따라 답이 달라진다"를 보여주는 순서입니다.

1. 정상 상태에서 시작합니다. 종합 판정 "정상", 조명은 청록색입니다.
2. **전조등 이상**을 주입합니다. 낮이라 1등급 "여유 있게 정비"가 나옵니다.
3. 시연 콘솔의 **밤/낮** 버튼(또는 앱의 **낮** 칩)을 눌러 **밤**으로 바꿉니다. 같은 고장이 야간 가중치 +3을 받아 3등급 "즉시 정비"로 올라갑니다.
4. 칩을 두 번 더 눌러 자동으로 되돌리고, **빗길 타이어**를 주입합니다. 와이퍼가 작동해 비로 판단되고 3등급이 됩니다.
5. **브레이크 + ABS**를 주입합니다. 환경과 관계없이 4등급 "즉시 정차"가 나오고 화면이 점멸합니다.
6. **모두 해제**로 정상으로 돌아옵니다.

## 시연 영상 녹화

에뮬레이터 화면과 앱 소리를 장면별로 녹화해 자막이 들어간 영상 한 편으로 합치는 스크립트가 있습니다. `ffmpeg` 와 `ffprobe` 가 PATH에 있어야 합니다.

```
python tools/record_demo.py [출력 폴더] [장면 번호...]
python tools/compose_demo.py <클립 폴더> <출력.mp4>
```

- `record_demo.py` 는 장면마다 `sceneNN.webm` 을 만듭니다. 출력 폴더를 생략하면 `captures/demo_video/` 에 저장하고, 장면 번호를 주면 그 장면만 다시 찍습니다.
- `compose_demo.py` 는 클립을 1080p MP4로 이어 붙이고 `tools/demo_captions.py` 의 자막을 입힙니다.
- 녹화 전에 앱을 새로 띄우고 고장을 모두 해제한 뒤 시연 환경을 낮·맑음으로 고정합니다. 장면 구성과 대기 시간은 `record_demo.py` 안에서 고칩니다.

## 동작 방식

```text
Pleos Vehicle SDK ──▶ PleosSignalSource ──▶ FaultDetector ──▶ SeverityEngine ──▶ 화면
 경고등·전조등·와이퍼     0.5초마다 읽기        신호 → 고장       심각도 + 환경 → 등급
```

- **긴급도 점수** = 기본 심각도(1~3) + 환경 가중치 합계
- **등급 바닥**: 안전 핵심 고장은 환경과 관계없이 최소 등급을 보장합니다 (과열 3등급, 브레이크 + ABS 4등급 등).
- **주차 브레이크 선확인**: 브레이크 경고등만 켜져 있으면 고장이 아니라 "주차 브레이크 확인" 안내로 처리합니다.
- 등급은 규칙으로만 정합니다. 원인 · 위험 · 할 일의 설명 문구와 음성 문장은 Gemini 키가 있으면 AI가 작성하고, 키가 없거나 호출이 실패하면 규칙표의 고정 문구를 씁니다([F6](#통합-기능-회의-prd-f1f7)).
- 앱은 차량 신호를 읽기만 합니다. SDK의 제어(set) API는 호출하지 않습니다.

| 환경 | 적용 부품군 · 가중치 |
|---|---|
| 야간 | 등화 +3 |
| 악천후 | 와이퍼 +3, 타이어·제동 +2 |
| 고속도로 | 타이어·제동·조향 +1 |
| 남은 거리 50km 이상 | 냉각·구동 +1 |

규칙표는 `app/src/main/java/com/cardoctor/demo/engine/Rules.kt` 에 있습니다. `// 초안` 표시가 있는 항목은 팀 검토가 필요합니다.

## 프로젝트 구조

```text
app/src/main/java/com/cardoctor/demo/
├── engine/        판정 엔진 (순수 Kotlin, Android 의존성 없음): Model, Rules, SeverityEngine
├── vehicle/       Pleos Vehicle SDK로 신호 읽기: PleosSignalSource
├── demo/          PC 시연 도구가 보내는 명령 수신: DemoCommandReceiver
├── ui/            화면: DashboardScreen, AssistCards, Car3DView, CarDiagram, Palette
├── MainViewModel  신호 → 환경 → 판정을 묶어 화면 상태로 만듦
└── MainActivity
app/src/main/assets/car3d/   3D 뷰어 (three.js, 차량 모델)
app/src/test/                판정 엔진 단위 테스트
tools/demo_console.py        GUI 시연 콘솔
scripts/demo.cmd, demo.sh    명령줄 시나리오 주입
docs/screenshots/            README 스크린샷
```

## 디자인

쇼룸의 차량과 앰비언트 라이트를 콘셉트로 했습니다. 피아노 블랙 배경 위에 유리 질감 패널을 얹고,
종합 등급에 따라 화면 전체의 조명 색이 바뀝니다(정상 청록, 주의 호박색, 위험 적색). 4등급은 다이얼과 해당 부위가 점멸합니다.
색과 서체 정의는 `ui/Palette.kt` 에 있습니다. GUI 시연 콘솔도 같은 디자인 언어를 씁니다.

차량은 3D 뷰어로 표시합니다(`ui/Car3DView.kt`, `app/src/main/assets/car3d/`). 타이어·휠·전조등은 모델의 실제 메시가 빛나고,
모델에 별도 메시가 없는 부위(와이퍼, 구동·냉각, 배터리, 실내, 조향)는 해당 위치의 발광으로 표시합니다.
WebGL이나 모델을 불러올 수 없는 환경에서는 2D 평면도(`ui/CarDiagram.kt`)로 대체됩니다.

## 크레딧과 라이선스

- 차량 3D 모델: This work is based on "Hyundai Ioniq 5 - Lowpoly"
  (https://sketchfab.com/3d-models/hyundai-ioniq-5-lowpoly-675e78311e8440d88714bd212cb7a8fb)
  by andikapratamaw (https://sketchfab.com/andikapratamaw) licensed under CC-BY-4.0
  (http://creativecommons.org/licenses/by/4.0/). 도장 재질을 조정하고 고장 표시용 발광을 추가했습니다.
  원본 라이선스 파일은 `app/src/main/assets/car3d/ioniq5/license.txt` 에 있습니다.
- 렌더링: [three.js](https://threejs.org) r160, MIT License.
- 서체: [Pretendard](https://github.com/orioncactus/pretendard), SIL Open Font License 1.1.
- 지도 엔진: [MapLibre GL JS](https://maplibre.org) 4.7.1, BSD-3-Clause. 앱 안(`app/src/main/assets/map/vendor/`)에 들어 있어 실행 중에 외부 코드를 받지 않습니다. 라이선스 원문은 같은 폴더의 `LICENSE.txt`.
- 지도 데이터·타일: © [OpenStreetMap](https://www.openstreetmap.org/copyright) contributors, [OpenMapTiles](https://openmaptiles.org), 타일 제공 [OpenFreeMap](https://openfreemap.org). 지도 화면 아래에 출처를 표시합니다. 지도 그림(타일)과 글꼴은 인터넷에서 받으므로, 연결이 없으면 지도 대신 안내 문구가 뜨고 목록·안내 시작은 그대로 동작합니다.

## 알려진 제약

- 경고등 읽기(`Safety.getTelltalesInfo`)는 비공개 권한 `PRIVATE_CAR_SAFETY`가 필요합니다. 권한이 없으면 앱은 **공개 신호 모드**로 바뀌어 타이어·구동 배터리·전조등·와이퍼·기어 신호로 계속 진단합니다. 신호별 권한과 에뮬레이터 상태는 [docs/SIGNAL_MAP.md](docs/SIGNAL_MAP.md)에 정리했습니다.
- 타이어 실측 공기압(`TIRE_PRESSURE`)은 v2.1 에뮬레이터에 없습니다. 실차에서 값이 오면 바퀴별로 판정하고, 없으면 "센서값 없음"과 TPMS 경고등으로 판정합니다. 바퀴별 시연은 GUI 시연 콘솔의 타이어 시연값으로 하며, 시연값 판정에는 화면과 리포트에 "시연값"이 표시됩니다.
- 전조등·와이퍼의 실제 고장은 에뮬레이터에서 재현할 수 없어, 전조등 고장은 LED 헤드램프 경고등으로 표현합니다.
- 고장 주입은 에뮬레이터의 Virtual Console에 의존합니다. 실차에는 이 콘솔이 없습니다.
- Vehicle SDK 2.3.2에는 차속(`PERF_VEHICLE_SPEED`)을 읽는 공개 메서드가 없어, 속도는 위치 신호에서 계산합니다. 터널 등 위치를 못 받는 구간에서는 기어 신호로만 판단합니다.
- 에뮬레이터에는 마이크 입력이 없어 음성 질문은 GUI 시연 콘솔의 질문 버튼으로 시연합니다. 버튼은 실제 음성과 같은 해석·답변 경로를 탑니다.
- 에뮬레이터 기준으로만 검증했습니다.

## 운전 중 안전 (화면 잠금 · 음성)

- **주행 판단**: 기어 신호(`CAR_DRIVE`)가 P·N이 아니거나, 위치 신호로 잰 속도가 5km/h 이상이면 주행 중입니다. D단에서 신호 대기(0km/h)도 Android Automotive의 "idling"처럼 주행 중으로 봅니다. 칩에 "기어 D · 46km/h"처럼 근거가 표시됩니다.
- **자동 주행 환경** (공개 신호만 사용, 칩을 누르면 자동 → 강제 ON → 강제 OFF)

  | 환경 | 자동 판단 |
  |---|---|
  | 고속도로 | 내비가 알려 주는 현재 도로 제한속도 80km/h 이상, 또는 속도 80km/h 이상 |
  | 남은 거리 50km 이상 | 내비 안내 중 목적지까지 남은 거리 |
  | 주행 중 | 기어 D·R, 또는 속도 5km/h 이상 |

  속도는 FusedLocation과 GPS 중 최근 5초 안의 가장 큰 값을 씁니다. 한쪽이 늦게 들어와도 주행 중이 풀리지 않게 하기 위해서입니다.
- **주행 중 잠금**: 자세히보기와 정비소 리포트·QR은 "정차 후"로 바뀌고, 누르면 "음성으로 물어보세요" 안내만 뜹니다. 주행이 시작되면 열려 있던 상세·리포트 창도 닫힙니다.
- **음성 질문**: 상단 마이크 버튼 → Pleos SpeechToText(`ON_DEVICE`, `ko_KR`). 한 번에 한 질문, 8초 뒤 자동 종료. 답은 화면과 음성으로 함께 나옵니다.

  | 질문 예 | 동작 |
  |---|---|
  | "지금 정비소 가야 돼?" | 가장 급한 항목의 등급 · 이유 · 할 일 |
  | "왜 위험해?" | 위험 이유와 할 일 (AI 설명) |
  | "정비소 안내해줘" | 가장 가까운 정비소로 길안내 시작 (운전자가 요청했을 때만) |
  | "리포트 보여줘" | 정차 중이면 리포트·QR, 주행 중이면 정차 후 안내 |

- 답을 말하기 전에 마이크를 닫아, 앱이 자기 음성을 명령으로 알아듣지 않습니다.

## 시연 안정성

- **시연 환경 고정 (낮·맑음)**: GUI 시연 콘솔 버튼 하나로 밤·날씨를 강제로 고정하고 고속도로·장거리를 끕니다. 실시간 날씨나 시각 때문에 시연 중 등급이 바뀌지 않습니다.
- 고장이 없어도 OVERALL 카드의 **정비소 리포트 · QR** 로 바로 들어갈 수 있습니다.
- 앱에는 시연용 화면이 없습니다. 고장 주입과 시연값은 모두 PC의 GUI 시연 콘솔에서 합니다.

## 보안 (API 키)

- 팀 키는 `api-keys.properties`에 있고 빌드 때 `BuildConfig`로 들어갑니다. 받아서 바로 빌드해 볼 수 있도록 저장소에 함께 두었습니다.
- **이 저장소는 현재 공개 상태이므로 이 키는 노출된 것으로 봐야 합니다.** 아래 제한으로 피해 범위를 막아 두었지만, 공개가 필요한 기간이 끝나면 이 파일을 지우고 키를 폐기한 뒤 새로 발급해야 합니다. 파일을 지워도 git 기록에는 남으므로 재발급이 꼭 필요합니다.
- 자기 키를 쓰려면 `local.properties`(git 제외)에 같은 이름으로 넣으면 그 값이 우선합니다.
- **Gemini**: Gemini용 키는 구글 정책상 서비스 계정에 바인드되어 "Android 앱" 제한을 걸 수 없습니다. 대신 세 겹으로 피해 범위를 막았습니다.
  - API 제한: 이 키로는 Gemini API만 호출됩니다.
  - 무료 등급: 결제 수단이 없어 키가 새도 요금이 청구되지 않습니다.
  - 하루 상한: 앱이 쓰는 `gemini-3.5-flash-lite`를 프로젝트당 하루 300건으로 낮췄습니다. 넘으면 앱은 규칙표 문구로 설명합니다.
  - 앱은 요청에 `X-Android-Package` · `X-Android-Cert`를 함께 보내므로, 일반 API 키로 바꾸면 Android 앱 제한을 바로 쓸 수 있습니다.
- **Tmap**: SK open API 콘솔에서 일일 호출 한도를 낮게 두고, POI 검색 API만 켭니다. 앱은 1km 이상 이동했을 때만 다시 검색합니다.
- 실서비스라면 두 호출을 서버 프록시로 옮기고 앱에는 키를 넣지 않습니다.

## 통합 기능 (회의 PRD F1~F7)

팀 회의에서 정한 추가 기능을 이 UI 위에 통합했습니다. 등급은 지금처럼 규칙 엔진만 정하고, 외부 API는 설명·안내만 합니다.

| ID | 기능 | 구현 | 코드 |
|---|---|---|---|
| F1 | 브레이크·타이어 공기압 | 3D 차량 아래 **BRAKE · TIRES** 카드. 브레이크 상태(정상/주차 브레이크/ABS/브레이크 시스템)와 바퀴 4개 공기압. 바퀴 값이 기준(차량의 `CRITICALLY_LOW_TIRE_PRESSURE`, 없으면 137kPa)보다 낮으면 "뒤 오른쪽 타이어 공기압 저하"처럼 바퀴를 짚어 판정 | `engine/Model.kt`, `SeverityEngine.kt`, `vehicle/PleosSignalSource.kt`, `ui/AssistCards.kt` |
| F2 | 주행 중 가독성 | 고장 카드는 큰 한 줄 요약 + 등급 + 판정 근거 + 할 일만. 원인·위험은 **자세히보기** | `ui/DashboardScreen.kt`, `ui/AssistCards.kt` |
| F3 | 음성 안내 (3등급 이상) | 3등급 이상이 새로 뜰 때 한 번 읽음. Pleos TextToSpeech(ON_DEVICE, ko_KR) | `services/Speaker.kt` |
| F4 | 가까운 정비소·주차장 + 안내 시작 | Tmap POI 검색(거리순), **안내 시작**을 눌렀을 때만 Pleos NaviHelper로 길안내. NEARBY의 **지도에서 보기**는 정차 중에만 OpenFreeMap 다크 지도(OpenStreetMap 데이터, 키 없음)에 내 위치·정비소 1~3·주차장 P를 핀으로 보여 주고, 고른 곳까지 **Tmap 자동차 경로**를 파란 선으로 그려 "약 3분 · 910m"처럼 미리 보여 줍니다(경로 미리보기). 실제 길안내는 차량 내비가 합니다. 장소 검색은 한국 데이터가 정확한 Tmap, 지도 그림만 OpenFreeMap | `services/TmapPlaces.kt`, `services/NaviLauncher.kt`, `ui/PlacesMap.kt`, `assets/map/` |
| F5 | 진단 결과 QR | **정비소 사전 리포트 · QR**. 팀 PRD(정비소 사전 리포트) JSON schemaVersion 1을 QR로, JSON 파일 저장 | `engine/ServiceReport.kt` |
| F6 | AI 설명 | Gemini(`gemini-3.5-flash-lite`, 실패 시 `gemini-3.5-flash`)가 요약·원인·위험·할 일·음성 문장 작성. 키가 없거나 실패하면 규칙표 문구 | `services/GeminiExplainer.kt` |
| F7 | 날씨 연동 | 현재 위치 날씨(MET Norway, 대체 Open-Meteo, 키 불필요)가 비·눈이면 "악천후" 가중치. 와이퍼 신호와 함께 판단 | `services/LocationWeather.kt` |

### API 키

팀 키가 `api-keys.properties`에 들어 있어 **받아서 바로 빌드하면 Tmap·Gemini가 연결됩니다.** 자기 키를 쓰려면 `local.properties`(git 제외)에 같은 이름으로 넣으면 그 값이 우선합니다. 키가 없으면 해당 기능만 꺼지고 나머지는 동작합니다. Gemini는 하루 300건을 팀 전체가 나눠 씁니다.

```
TMAP_APP_KEY=...
GEMINI_API_KEY=...
```

키를 넣거나 바꾼 뒤에는 `./gradlew clean installDebug` 로 새로 빌드하세요. `clean` 없이 빌드하면 이전 값이 앱에 그대로 남아 "키 없음"으로 표시될 수 있습니다.

### 에뮬레이터 준비 (F3·F4·F7)

- 에뮬레이터 GPS를 내비 엔진 위치(판교)와 맞춥니다. 기본값(미국)이면 정비소 검색과 길안내가 실패합니다.

  ```
  adb emu geo fix 127.094681 37.4127212
  ```
- 앱이 위치 권한을 요청하면 허용합니다.
- Pleos TTS는 v2.1 에뮬레이터에서 `HYBRID` 모드 또는 `ko` 로케일이면 소리가 나지 않습니다(`ON_DEVICE_ENGINE_ERROR`). 앱은 `ON_DEVICE` + `ko_KR` 을 씁니다.
