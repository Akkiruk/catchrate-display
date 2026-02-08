"""
Fix all non-en_us lang files to match the new extra-details format.
Second pass: also handles fullwidth exclamation ！ and trailing spaces.
"""
import json
import os
import re

LANG_DIR = os.path.join(os.path.dirname(__file__), 
    "common", "src", "main", "resources", "assets", "catchrate-display", "lang")


def strip_excl(val):
    """Strip both ASCII and fullwidth exclamation marks from the end, and trailing spaces."""
    return val.rstrip("!！ ")


def fix_lang(data: dict) -> dict:
    d = dict(data)
    
    # guaranteed_catch: remove trailing exclamation
    k = "catchrate.ball.guaranteed_catch"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # multiplier_always: set to empty (ball line already shows multiplier)
    k = "catchrate.ball.multiplier_always"
    if k in d:
        d[k] = ""
    
    # ancient: simplify
    k = "catchrate.ball.ancient"
    if k in d:
        val = d[k]
        val = re.sub(r'\s*\(1x[^)]*\)', '', val)
        val = re.sub(r':\s*', ' (', val, count=1)
        if '(' in val and not val.endswith(')'):
            val = val.rstrip() + ')'
        d[k] = val
    
    # quick.effective: remove "5x" and exclamation
    k = "catchrate.ball.quick.effective"
    if k in d:
        val = d[k]
        val = re.sub(r'5x\s*', '', val)
        val = strip_excl(val).strip()
        if val:
            val = val[0].upper() + val[1:]
        d[k] = val
    
    # quick.ineffective: clean trailing excl
    k = "catchrate.ball.quick.ineffective"
    if k in d:
        d[k] = strip_excl(d[k]).strip()
    
    # timer.turn_info: CRITICAL - remove ": %.1fx" 
    k = "catchrate.ball.timer.turn_info"
    if k in d:
        val = d[k]
        val = re.sub(r'\s*[:：]\s*%.?[0-9]*[df]x?', '', val)
        d[k] = val
    
    # dive.underwater: remove excl
    k = "catchrate.ball.dive.underwater"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # dive.need_underwater: clean
    k = "catchrate.ball.dive.need_underwater"
    if k in d:
        d[k] = strip_excl(d[k]).strip()
    
    # moon.night_phase: CRITICAL - remove ": %.0fx"
    k = "catchrate.ball.moon.night_phase"
    if k in d:
        val = d[k]
        val = re.sub(r'\s*[:：]\s*%.?[0-9]*[df]x?', '', val)
        d[k] = val
    
    # net.effective: remove excl
    k = "catchrate.ball.net.effective"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # nest.effective: CRITICAL - remove ": %.1fx" 
    k = "catchrate.ball.nest.effective"
    if k in d:
        val = d[k]
        val = re.sub(r'\s*[:：]\s*%.?[0-9]*[df]x?', '', val)
        d[k] = val
    
    # nest.ineffective: clean
    k = "catchrate.ball.nest.ineffective"
    if k in d:
        d[k] = strip_excl(d[k]).strip()
    
    # fast.effective: remove excl
    k = "catchrate.ball.fast.effective"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # beast.ultra_beast: remove excl
    k = "catchrate.ball.beast.ultra_beast"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # beast.penalty: remove "0.1x" prefix
    k = "catchrate.ball.beast.penalty"
    if k in d:
        val = d[k]
        val = re.sub(r'0\.1x\s*', '', val)
        val = strip_excl(val).strip()
        if val:
            val = val[0].upper() + val[1:]
        d[k] = val
    
    # dream.sleeping: remove excl
    k = "catchrate.ball.dream.sleeping"
    if k in d:
        d[k] = strip_excl(d[k])
    
    # dream.need_sleep: clean
    k = "catchrate.ball.dream.need_sleep"
    if k in d:
        d[k] = strip_excl(d[k]).strip()
    
    # level.effective: CRITICAL - remove "%.0fx" format
    k = "catchrate.ball.level.effective"
    if k in d:
        val = d[k]
        val = re.sub(r'%.?[0-9]*[df]x?\s*', '', val)
        val = strip_excl(val).strip()
        if val:
            val = val[0].upper() + val[1:]
        d[k] = val
    
    # repeat.effective: remove "3.5x - " prefix
    k = "catchrate.ball.repeat.effective"
    if k in d:
        val = d[k]
        val = re.sub(r'3\.5x\s*[-–—]\s*', '', val)
        val = strip_excl(val).strip()
        if val:
            val = val[0].upper() + val[1:]
        d[k] = val
    
    # lure.effective: remove "4x " prefix
    k = "catchrate.ball.lure.effective"
    if k in d:
        val = d[k]
        val = re.sub(r'4x\s*', '', val)
        val = strip_excl(val).strip()
        if val:
            val = val[0].upper() + val[1:]
        d[k] = val
    
    return d


def main():
    files = sorted(f for f in os.listdir(LANG_DIR) if f.endswith('.json') and f != 'en_us.json')
    print(f"Found {len(files)} lang files to update")
    
    total_changes = 0
    for fname in files:
        fpath = os.path.join(LANG_DIR, fname)
        with open(fpath, 'r', encoding='utf-8') as f:
            data = json.load(f)
        
        fixed = fix_lang(data)
        
        changes = []
        for key in fixed:
            if key in data and fixed[key] != data[key]:
                changes.append(f"  {key}: {data[key]!r} -> {fixed[key]!r}")
        
        if changes:
            with open(fpath, 'w', encoding='utf-8', newline='\n') as f:
                json.dump(fixed, f, indent=2, ensure_ascii=False)
                f.write('\n')
            print(f"\n{fname}: {len(changes)} changes")
            for c in changes:
                print(c)
            total_changes += len(changes)
        else:
            print(f"{fname}: OK (no further changes)")
    
    print(f"\nTotal: {total_changes} changes across {len(files)} files")


if __name__ == '__main__':
    main()
