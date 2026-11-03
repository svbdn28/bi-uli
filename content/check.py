import json, collections, re
d = json.load(open("/Users/rafael/Programming/uli/content/questions.json"))
assert len(d) == 70
TOP = {"intro","opensource","cli","help","files","archives","pipes","scripts","hardware","network","users","perms","links"}
for i, q in enumerate(d, 1):
    assert q["id"] == i
    assert q["topic"] in TOP
    nc = sum(o["correct"] for o in q["options"])
    assert q["choose"] == nc, i
    if q["kind"] == "truefalse":
        assert [o["text"] for o in q["options"]] == ["True", "False"], i
        assert nc == 1
    elif q["kind"] == "multi": assert nc > 1, i
    else: assert q["kind"] == "single" and nc == 1, i
    sk = [q["questionSk"], q["explanation"], q.get("hook", "")] + [o.get("note", "") for o in q["options"]]
    for s in sk: assert "—" not in s, (i, s)
    if "hook" in q: assert len(q["hook"].split()) <= 12, (i, q["hook"])
    if "demo" in q:
        assert len(q["demo"].split("\n")) <= 5, i
        assert q["demo"].startswith("$ "), i
    allt = q["question"] + "".join(o["text"] for o in q["options"])
    assert "–" not in allt and "‘" not in allt, i
    assert not re.search(r"\(choose|True or False\?", q["question"]), i
    assert q["source"] == "ok" or q["source"].startswith("disputed: ")
print("OK", collections.Counter(q["topic"] for q in d))
print("disputed", [q["id"] for q in d if q["source"] != "ok"])
