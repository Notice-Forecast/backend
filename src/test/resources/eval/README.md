# 정답셋 라벨링

AI 추출·판정 정확도를 재기 위한 정답이다. 공지 20건은 Claude가 초안을 적고, Gemini가 원문·첨부와 교차검증한 뒤, 사람(`labeler`: A)이 원문과 대조해 검수했다. 새 공지를 더할 때는 초안에 `labeler: "C"`를 적고, 검수가 끝나면 `"A"`로 바꾼다.

- `scope: FULL` 10건: 마감·대상·조건·서류·판정을 모두 적는다.
- `scope: BASIC` 10건: `deadline`과 `target`만 적는다.

## 체크리스트

이 표와 원문 탭 하나, JSON 파일 하나만 띄워 두고 위에서부터 차례로 적는다.

| # | 범위 | 글 번호 (원문) | 제목 |
| --- | --- | --- | --- |
| 1 | FULL | [277319](https://design.catholic.ac.kr/design/community/notice.do?mode=view&articleNo=277319) | 연구인턴 선발 |
| 2 | FULL | [277757](https://csie.catholic.ac.kr/csie/community/notice.do?mode=view&articleNo=277757) | 기수료생 졸업요건 제출 |
| 3 | FULL | [278044](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278044) | 국가근로장학금 1차 |
| 4 | FULL | [278164](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278164) | CHUBB 장학생 선발 |
| 5 | FULL | [278177](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278177) | 프란치스코관 기숙사 모집 |
| 6 | FULL | [278193](https://design.catholic.ac.kr/design/community/notice.do?mode=view&articleNo=278193) | 일반대학원 신입생 모집 |
| 7 | FULL | [278195](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278195) | KISTI 청년인턴 채용 |
| 8 | FULL | [278200](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278200) | 신임교원 리서치 콜로키엄 |
| 9 | FULL | [278221](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278221) | 청년창업농 장학생 모집 |
| 10 | FULL | [278243](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278243) | 오픈배지 장학금 |
| 11 | BASIC | [277899](https://csie.catholic.ac.kr/csie/community/notice.do?mode=view&articleNo=277899) | AI 융합 창업경진대회 |
| 12 | BASIC | [277972](https://csie.catholic.ac.kr/csie/community/notice.do?mode=view&articleNo=277972) | AI:VERSE 페스티벌 |
| 13 | BASIC | [278042](https://csie.catholic.ac.kr/csie/community/notice.do?mode=view&articleNo=278042) | 학부 학술제·전문가 특강 |
| 14 | BASIC | [278051](https://design.catholic.ac.kr/design/community/notice.do?mode=view&articleNo=278051) | 헬스케어디자인 공간 탐방 |
| 15 | BASIC | [278057](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278057) | 기초학력 특강 요구조사 |
| 16 | BASIC | [278103](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278103) | 국가장학금 1차 |
| 17 | BASIC | [278111](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278111) | 복수전공/부전공 변경 |
| 18 | BASIC | [278139](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278139) | 총학생회 선거 |
| 19 | BASIC | [278140](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278140) | 총장 간담회 |
| 20 | BASIC | [278157](https://www.catholic.ac.kr/ko/campuslife/notice.do?mode=view&articleNo=278157) | AI인재존 체험 부스 |

## 지킬 것

- AI가 만든 카드를 보기 전에 적는다. 이미 본 공지는 정답셋에서 빼고 다른 공지로 바꾼다.
- 원문 페이지(`notice.do?mode=view&articleNo={articleNo}`)와 첨부를 직접 읽고 적는다.
- 저장소가 공개이므로 본문 문장, 이름, 학번, 연락처를 붙여 넣지 않는다. 라벨 값만 적는다.
- 원문에 없는 것은 추측해서 채우지 않는다. 없으면 `null` 또는 `[]`로 둔다.

## 순서

1. `notices/{articleNo}.json`을 연다.
2. 원문 페이지와 첨부를 끝까지 읽는다.
3. 아래 규칙대로 `deadline`, `target`을 적는다. BASIC은 여기서 끝.
4. FULL은 `conditions`, `documents`를 적고, `personas.json`의 P1~P3 각각에 대해 `verdicts`를 적는다.
5. JSON 형식이 깨지지 않았는지 확인한다.

## 항목

| 항목 | 적는 법 |
| --- | --- |
| `deadline` | 신청 마감 날짜 `YYYY-MM-DD`. 시각까지 적혀 있으면 `YYYY-MM-DDTHH:mm:ss+09:00`. 마감이 없으면 `null` |
| `target` | 원문이 말하는 대상을 한 줄로 요약 (예: "3학년 1학기 진학 예정 학부생"). 원문 문장을 그대로 옮기지 않는다 |
| `conditions` | 자격 조건마다 `{ "field", "op", "values" }` 하나. 빈 항목 하나가 들어 있으니 채우고, 조건이 더 있으면 복사해 늘린다. 조건이 없으면 빈 항목을 지워 `[]`로 둔다. 근거 문장은 적지 않는다 |
| `documents` | 신청할 때 내야 하는 서류 이름 목록 (예: `["추천서", "재학증명서"]`). 트리니티·한국장학재단 신청처럼 내용을 작성해 제출하는 온라인 신청도 넣는다. 이름만 적는 참가 신청(구글폼 등)과 선발 뒤에 내는 서류는 넣지 않는다. 없으면 `[]` |
| `verdicts` | 페르소나별 `ELIGIBLE`(해당), `CONDITIONAL`(조건부), `HIDDEN`(숨김) |

### conditions

| field | 쓰는 경우 | op와 values |
| --- | --- | --- |
| `GRADE` | 학년 | `IN` + `["3", "4"]` |
| `DEPARTMENT` | 학과 | `IN` 또는 `NOT_IN` + 학과 이름 |
| `ENROLLMENT_STATUS` | 학적 | `IN`/`NOT_IN` + `ENROLLED`(재학), `LEAVE`(휴학), `RETURNING`(복학 예정), `GRADUATE_STUDENT`(대학원생), `COMPLETED`(수료) |
| `GPA` | 평점 | `GTE`/`LTE` + `["3.0"]` |
| `SEMESTER_COUNT` | 이수 학기 | `GTE`/`LTE` + `["4"]` |
| `DORM_RESIDENT` | 기숙사생 | `IN` + `["TRUE"]`(기숙사생만) 또는 `["FALSE"]`(기숙사생 제외) |
| `INTERNATIONAL` | 유학생 | `IN` + `["TRUE"]` 또는 `["FALSE"]` |
| `SELF_CHOICE` | 희망·의사를 묻는 조건 (예: "참여를 희망하는 자") | `MANUAL` + `[]` |
| `FREE_TEXT` | 위로 나타낼 수 없는 조건 (예: "교원", "소득 8구간 이하") | `MANUAL` + `[]` |

### 붙여넣기용 조건

필요한 줄만 복사해 `conditions` 안에 붙이고 값만 바꾼다. 여러 줄이면 줄 끝에 쉼표를 붙이되, 마지막 줄에는 붙이지 않는다.

```json
{ "field": "GRADE", "op": "IN", "values": ["3", "4"] }
{ "field": "GRADE", "op": "NOT_IN", "values": ["1"] }
{ "field": "DEPARTMENT", "op": "IN", "values": ["컴퓨터정보공학부"] }
{ "field": "DEPARTMENT", "op": "NOT_IN", "values": ["의예과"] }
{ "field": "ENROLLMENT_STATUS", "op": "IN", "values": ["ENROLLED"] }
{ "field": "ENROLLMENT_STATUS", "op": "IN", "values": ["ENROLLED", "LEAVE"] }
{ "field": "ENROLLMENT_STATUS", "op": "IN", "values": ["COMPLETED"] }
{ "field": "ENROLLMENT_STATUS", "op": "IN", "values": ["GRADUATE_STUDENT"] }
{ "field": "ENROLLMENT_STATUS", "op": "NOT_IN", "values": ["LEAVE"] }
{ "field": "GPA", "op": "GTE", "values": ["3.0"] }
{ "field": "GPA", "op": "LTE", "values": ["4.5"] }
{ "field": "SEMESTER_COUNT", "op": "GTE", "values": ["4"] }
{ "field": "SEMESTER_COUNT", "op": "LTE", "values": ["7"] }
{ "field": "DORM_RESIDENT", "op": "IN", "values": ["TRUE"] }
{ "field": "DORM_RESIDENT", "op": "IN", "values": ["FALSE"] }
{ "field": "INTERNATIONAL", "op": "IN", "values": ["TRUE"] }
{ "field": "INTERNATIONAL", "op": "IN", "values": ["FALSE"] }
{ "field": "SELF_CHOICE", "op": "MANUAL", "values": [] }
{ "field": "FREE_TEXT", "op": "MANUAL", "values": [] }
```

값을 바꾸는 법:

- **학년:** `"1"`~`"4"` 중 해당하는 것을 모두 적는다. 예: 2~3학년 → `["2", "3"]`.
- **학과:** 학교 사이트에 나온 이름을 그대로 적는다. 여러 학과면 모두 적는다.
- **학적:** 재학 `ENROLLED`, 휴학 `LEAVE`, 복학 예정 `RETURNING`, 수료 `COMPLETED`, 대학원생 `GRADUATE_STUDENT`.
- **평점·학기:** 숫자를 문자열로 적는다. "이상"은 `GTE`, "이하"는 `LTE`.
- **기숙사생·유학생:** 해당자만 대상이면 `TRUE`, 해당자를 빼면 `FALSE`.
- **SELF_CHOICE·FREE_TEXT:** 값 없이 그대로 쓴다. 중복 참여 금지, 소득 구간, 교원 대상처럼 위 항목으로 나타낼 수 없는 조건은 `FREE_TEXT`다.

예: "2~3학년 재학생, 평점 3.0 이상, 다른 근로장학 중복 불가"

```json
"conditions": [
  { "field": "GRADE", "op": "IN", "values": ["2", "3"] },
  { "field": "ENROLLMENT_STATUS", "op": "IN", "values": ["ENROLLED"] },
  { "field": "GPA", "op": "GTE", "values": ["3.0"] },
  { "field": "FREE_TEXT", "op": "MANUAL", "values": [] }
],
```

### 페르소나 (`personas.json`)

| | 주전공 | 추가 전공 | 학년 | 학적 | 평점 | 기숙사생 | 유학생 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P1 | 컴퓨터정보공학부 | 없음 | 3 | 재학 | 3.8 | 아님 | 아님 |
| P2 | 공간디자인ㆍ소비자학과 | 컴퓨터정보공학부(복수전공) | 2 | 재학 | 3.2 | 맞음 | 아님 |
| P3 | 경영학과 | 없음 | 4 | 휴학 | 2.8 | 아님 | 맞음 |

### verdicts

- 확인 가능한 조건을 모두 만족하면 `ELIGIBLE`.
- 하나라도 만족하지 않으면 `HIDDEN`.
- 만족하지 않는 조건은 없지만 `FREE_TEXT`처럼 확인할 수 없는 조건이 있으면 `CONDITIONAL`.
- `SELF_CHOICE`는 판정에서 뺀다.
- 학과는 주전공이 맞으면 충족, 추가 전공만 맞으면 `CONDITIONAL`.
- 학생이 신청할 수 없는 공지(교원 대상, 대학원 신입생 모집 등)는 `HIDDEN`.
- 마감이 지났는지는 판정에 넣지 않는다.
