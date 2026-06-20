# 오늘식단 Android MVP

`http://www.shlu.or.kr/2015/MonthMenu`의 월간 식단표를 바탕으로 매일 조식, 중식, 석식을 빠르게 확인하는 Android 앱입니다.

## 현재 기능

- 오늘 식단 우선 표시
- 날짜별 조식/중식/석식 탭
- 이번 주 식단 요약
- 알레르기/주의 재료 태그
- 2026년 6월 식단표 이미지 OCR 데이터 기본 탑재
- 원본 페이지 새로고침 시도 및 캐시 저장
- 조식 7:30, 중식 11:20, 석식 17:20 매일 알림 예약
- 원본 연결 실패 시 앱에 포함된 2026년 6월 데이터 표시

## 데이터 구조

기본 데이터는 `app/src/main/assets/meal_current.json`에 있습니다. 월별 보관 파일은 `app/src/main/assets/meal_YYYY_MM.json` 형태로 저장합니다.

매월 업데이트는 `tools/update-month-menu.ps1`가 담당합니다. 이 스크립트는 원본 게시판의 최신 식단표 글을 찾고, 이미지를 내려받은 뒤 날짜/식사별로 잘라 Tesseract OCR 결과를 JSON으로 만듭니다.

```powershell
powershell -ExecutionPolicy Bypass -File .\tools\update-month-menu.ps1
.\gradlew.bat assembleDebug
```

생성되는 파일:

- `source-data/menu-YYYY-MM/page-*.jpg`
- `source-data/menu-YYYY-MM/crops/*.png`
- `app/src/main/assets/meal_YYYY_MM.json`
- `app/src/main/assets/meal_current.json`
- `app/src/main/assets/meal_metadata.json`

## GitHub Pages 자동 반영

`.github/workflows/update-menu-pages.yml`가 매월 27~31일 23:20 UTC에 실행됩니다. 한국 시간으로는 다음날 오전 8:20입니다. 원본 게시글이 월말에 올라오는 흐름을 감안해 며칠 동안 반복 확인하도록 잡았습니다. 필요하면 GitHub Actions의 `workflow_dispatch`로 수동 실행할 수도 있습니다.

GitHub 저장소에서 Pages를 Actions 배포 방식으로 켠 뒤, 앱의 `app/src/main/res/values/strings.xml`에 있는 `remote_menu_url`을 실제 Pages URL로 바꾸세요.

예시:

```xml
<string name="remote_menu_url">https://YOUR_GITHUB_USERNAME.github.io/YOUR_REPOSITORY/data/meal_current.json</string>
```

이후 앱은 실행 시 GitHub Pages의 최신 JSON을 먼저 읽고, 실패하면 앱에 포함된 마지막 데이터를 표시합니다.

OCR은 자동 초안입니다. 원본 이미지 글자가 작아 일부 메뉴명 오탈자가 남을 수 있으므로, 운영 배포 전에는 `meal_2026_06.json`의 `items`를 검수해 보정하는 흐름이 필요합니다.

## 빌드

```powershell
.\gradlew.bat assembleDebug
```

빌드 결과는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
