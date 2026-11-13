# CarDoctor (카닥터 AI) — Claude Code 안내

현대자동차그룹 차량용 앱 개발 공모전(Pleos Playground) 출품작이다. 차량 경고등·신호와 주행 상황(낮/밤, 날씨, 속도, 남은 거리)으로 "지금 정비소에 가야 하나?"를 4단계로 판정하는 Pleos Connect(AAOS) 앱이다.

**작업을 시작하기 전에 `HANDOFF.md`를 먼저 읽는다.** 거기에 다음이 정리돼 있다.
- 현재 상태와 에뮬레이터에서 확인할 항목
- 제출 전에 할 일(마감 2026-11-15 23:59, 저장소 Public 전환은 11/16)
- 팀이 정해야 할 것

## 규칙

- 등급은 `app/src/main/java/com/team/cardoctor/engine/RuleEngine.kt`만 정한다. LLM은 설명 문장만 쓴다. 판정표를 바꾸면 `RuleEngineTest`도 함께 바꾼다.
- Pleos SDK 시그니처(Vehicle 2.3.2, NaviHelper 3.0.5, TextToSpeech 3.3.1, SpeechToText 3.3.1, FusedLocation 1.1.3)는 AAR에서 직접 확인한 것이다. 리스너 파라미터 nullability는 SDK마다 다르니 컴파일러 에러를 따른다.
- 커밋·푸시는 사용자가 요청할 때만 한다.
- 긴급출동 번호(`MainViewModel.ROADSIDE_NUMBER`)는 아직 검증되지 않았다.

## 빌드·테스트

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease
```

경로에 한글이 있는 Windows에서는 `-PbuildRoot=C:/build/cardoctor`를 붙인다(테스트 ClassNotFoundException 우회). 화면 렌더는 `ScreenRenderTest`가 `docs/renders/`에 PNG로 저장한다.

팀 공통 PRD 문서(Claude Docs): https://claude.ai/artifact/MgAsHPhmBfu9jCMwQfbMMj
