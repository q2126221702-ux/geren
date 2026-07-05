"""从 7月5号 截图整理数据生成合并语法选择题题库."""
import json
from datetime import datetime
from pathlib import Path

ROOT = Path(__file__).parent.parent.parent
SRC_DIR = ROOT / "welearn-output" / "7月5号"
DATA_DIR = Path(__file__).parent.parent / "data"
MANIFEST = DATA_DIR / "manifest.json"
DATE_TAG = "20260705"
OUT_NAME = f"WE Learn_B1U4-U8_语法选择_{DATE_TAG}.json"
MANIFEST_ID = "welearn_b1u4u8_grammar"

# 答案以 WE Learn 教材截图（绿勾/绿字）为准
UNITS = [
    {
        "unit": "B1U4 Movies",
        "questions": [
            ("People are fascinated with the memorable cartoon characters ______ by Hayao Miyazaki (宫崎骏).", ["created", "played", "founded", "written"], "A"),
            ("The People's Republic of China was ______ in 1949.", ["find", "founding", "found", "founded"], "D"),
            ("The business has ______ from having one office to having twelve.", ["moved", "expanded", "moving", "expanding"], "B"),
            ("Did you have ______ in Disneyland last summer?", ["joy", "happiness", "fun", "wonder"], "C"),
            ("We are told that the key to success ______ hardwork.", ["lies in", "lies by", "lies for", "lies at"], "A"),
            ("Would you mind ______ me here at nine o'clock?", ["meet", "to meet", "meeting", "met"], "C"),
            ("I was so sleepy that I could hardly keep my eyes ______ yesterday evening.", ["opening", "open", "to be opened", "to open"], "B"),
            ("Jack is our math teacher's ______ student who always scores highest in math exams.", ["worst", "favorite", "loved", "liked"], "B"),
            ("Miranda's suggestion will ______ to argument.", ["turn", "seem", "grow", "lead"], "D"),
            ("Bruce Lee is best ______ his martial arts on the film screen.", ["remembered as", "thought for", "remembered for", "recalled for"], "C"),
        ],
    },
    {
        "unit": "B1U5 Our Earth",
        "questions": [
            ("The writer felt the need to make a ______ against racism in South Africa.", ["stand", "voice", "thought", "choice"], "A"),
            ("______ bad weather we had to have our physical education class in the classroom.", ["Because of", "Because", "Because of the", "Cause"], "C"),
            ("He was in a very low mood recently: he lost his job and ______ his wife left him.", ["yet", "on top of that", "also", "particularly"], "B"),
            ("Please ______ the TV set before you leave the sitting room.", ["turn on", "close", "turn off", "open"], "C"),
            ("We encourage students to ______ fully in the running of the college.", ["work", "participate", "come", "join"], "B"),
            ("A sitting room can be made into a guest bedroom by ______ adding a sofabed.", ["particularly", "also", "rather", "simply"], "D"),
            ("If you want to succeed, you must ______ average.", ["go beyond", "go after", "go against", "go before"], "A"),
            ("A new building ______ the view of my office window.", ["takes", "blocks", "conceals", "hides"], "B"),
            ("The role will be the biggest ______ of his acting career.", ["challenge", "examination", "task", "difficulty"], "A"),
            ("The dove is a ______ of peace.", ["sign", "animal", "symbol", "signal"], "C"),
        ],
    },
    {
        "unit": "B1U6 Part-time jobs",
        "questions": [
            ("The new student ______ well with other classmates.", ["got away", "got up", "got along", "got down"], "C"),
            ("With a failing memory, the old granny ______ remembering things.", ["finds it easy", "is able to", "can not", "has difficulty in"], "D"),
            ("Buy a computer and you will get a package of software ______.", ["for free", "for money", "for use", "without money"], "A"),
            ("We've been ______ the lost boy all over the town.", ["hunted for", "hunting for", "looking after", "looked after"], "B"),
            ("The composition needs ______ before it is handed in.", ["to check", "checking", "checked", "check"], "B"),
            ("The pen I am using now ______ my brother.", ["belongs to", "is belonged to", "belongs", "is belonging to"], "A"),
            ("Do you usually go to see the doctor ______ or with your mother?", ["by himself", "alone", "lonely", "yourself"], "B"),
            ("Jack will ______ of the department when the director is away on holiday.", ["charge", "in charge", "be in charge", "be charging"], "C"),
            ("The teacher gave the students her new telephone number so that they could call her ______.", ["if possible", "when necessary", "when possible", "for free"], "B"),
            ("Do you know why the author ______ the film 'The Wandering Earth' in his recent book?", ["said", "spoke", "mentioned", "told"], "C"),
        ],
    },
    {
        "unit": "B1U7 Health",
        "questions": [
            ("If you want to gain people's respect, be sure to ______ your promises.", ["live through", "live up to", "live with", "live on"], "B"),
            ("As the saying goes, all work and no play makes Jack a dull boy, so we should ______ work and play.", ["keep a good balance between", "both", "better", "just"], "A"),
            ("The wind is ______. Let's ______ the fence before a storm comes.", ["strong; strengthen", "strengthen; strength", "strength; strengthen", "strengthening; strong"], "A"),
            ("While people may watch television for up-to-the-minute news, it is unlikely that television ______ the newspaper completely.", ["replace", "have replaced", "replaced", "will replace"], "D"),
            ("My boss asked me to write a marketing report, but I have little experience ______ to marketing.", ["about", "related", "on", "concerning"], "B"),
            ("My cousin is very ______ in all kinds of after-school activities.", ["keen", "eager", "focused", "active"], "D"),
            ("Why ______ on clothes you don't need?", ["stick", "live", "waste", "fit"], "C"),
            ("The doctor ordered him to go on a ______ to lose weight.", ["vacation", "diet", "fasting", "job"], "B"),
            ("The car ______ speed as it went down the hill.", ["grew", "turned", "gained", "quickened"], "C"),
            ("Some animals get ______ when they see their reflections in the mirror.", ["confused", "angry", "sleepy", "sad"], "A"),
        ],
    },
    {
        "unit": "B1U8 Festivals",
        "questions": [
            ("I ______ to the summer vacation when I can visit my old friends in hometown.", ["look", "look forward", "look up", "look on"], "B"),
            ("It is impossible to change the committee's decision. It is ______.", ["final", "last", "unchanged", "fixed"], "A"),
            ("The company needs your immediate reply. It is ______ time for you to make up your mind.", ["good", "right", "high", "correct"], "C"),
            ("The Dragon Boat Festival ______ the fifth day of the fifth lunar month in China.", ["is", "falls on", "be", "fell on"], "B"),
            ("If you turn the envelope ______, the key will fall out.", ["upside down", "down", "in", "up"], "A"),
            ("You were ______ to be here one hour ago. What has happened to you?", ["waited", "supposed", "planned", "intended"], "B"),
            ("He ______ his things and went on a journey to Egypt.", ["gathered", "stuffed", "packed up", "made up"], "C"),
            ("______ the articles there are some photographs in the newspaper.", ["Along with", "With", "Along", "By"], "A"),
            ("The English teacher asked us to ______ the textbooks and notebooks from the desks and get ready to take an exam.", ["get", "put", "remove", "take"], "C"),
            ("There is a total ______ on smoking in the public space in many parts of China.", ["hate", "refusal", "no", "ban"], "D"),
        ],
    },
]


def letter_idx(letter: str) -> int:
    return {"A": 0, "B": 1, "C": 2, "D": 3}[letter]


def build_source():
    units = []
    for block in UNITS:
        qs = []
        for i, (stem, opts, ans) in enumerate(block["questions"], 1):
            qs.append({"sort": i, "stem": stem, "options": opts, "correct_answer": ans})
        units.append({"unit": block["unit"], "questions": qs})
    return {
        "title": "新标准高职公共英语·实用综合教程(第三版)1 — B1U4~U8 语法选择",
        "course": "新标准高职公共英语系列教材：实用综合教程(第三版)1",
        "exported_at": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
        "source": "WE Learn 截图手动整理（7月5号，25张）",
        "instruction": "Choose the best answer to complete each of the following sentences.",
        "units": units,
    }


def build_quiz():
    questions = []
    sort = 1
    for block in UNITS:
        unit = block["unit"]
        for stem, opts, letter in block["questions"]:
            idx = letter_idx(letter)
            opt_text = opts[idx]
            questions.append(
                {
                    "sort": sort,
                    "type": "单选题",
                    "title": f"【{unit}】{stem}",
                    "options": opts,
                    "correct_answer": f"{letter}. {opt_text}",
                    "your_answer": "",
                    "score": "0",
                    "full_score": 2.0,
                }
            )
            sort += 1
    return {
        "title": "WE Learn B1U4~U8 语法选择（合并）",
        "exported_at": datetime.now().isoformat(),
        "source": "WE Learn 截图整理（7月5号，25张）",
        "instruction": "Choose the best answer to complete each of the following sentences.",
        "questions": questions,
    }


def update_manifest(count: int):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    manifest["quizzes"] = [q for q in manifest["quizzes"] if q.get("id") != MANIFEST_ID]
    insert_at = next(
        (i for i, q in enumerate(manifest["quizzes"]) if q.get("id") == "welearn_b1u8"),
        len(manifest["quizzes"]),
    )
    insert_at += 1
    manifest["quizzes"].insert(
        insert_at,
        {
            "id": MANIFEST_ID,
            "title": "WE Learn B1U4~U8 语法选择",
            "file": OUT_NAME,
            "count": count,
        },
    )
    MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    SRC_DIR.mkdir(parents=True, exist_ok=True)
    src = build_source()
    src_path = SRC_DIR / OUT_NAME
    src_path.write_text(json.dumps(src, ensure_ascii=False, indent=2), encoding="utf-8")

    quiz = build_quiz()
    quiz_path = DATA_DIR / OUT_NAME
    quiz_path.write_text(json.dumps(quiz, ensure_ascii=False, indent=2), encoding="utf-8")

    update_manifest(len(quiz["questions"]))
    print(f"source: {src_path}")
    print(f"quiz:   {quiz_path} — {len(quiz['questions'])} 题")
    print(f"manifest updated ({MANIFEST_ID})")


if __name__ == "__main__":
    main()
