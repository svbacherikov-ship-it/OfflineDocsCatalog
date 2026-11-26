#!/usr/bin/env bash
set -euo pipefail
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell wm size 1080x1920
adb shell wm density 360
adb shell settings put global policy_control immersive.full=ru.amafor.offlinedocs.stable
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell am force-stop ru.amafor.offlinedocs.stable
adb shell run-as ru.amafor.offlinedocs.stable mkdir -p files/docs shared_prefs
for f in demo.docx demo.xlsx demo.xls demo.doc; do adb shell -T "run-as ru.amafor.offlinedocs.stable sh -c 'cat > files/docs/$f'" < "fixtures/$f"; done
adb shell -T "run-as ru.amafor.offlinedocs.stable sh -c 'cat > shared_prefs/catalog.xml'" < fixtures/catalog.xml
adb shell am start -n ru.amafor.offlinedocs.stable/ru.amafor.offlinedocs.MainActivity
python3 - <<'PY'
import subprocess, time, re, xml.etree.ElementTree as ET, pathlib
def adb(*args):
    return subprocess.check_output(['adb',*args],stderr=subprocess.STDOUT)
def nodes():
    adb('shell','uiautomator','dump','/sdcard/office.xml')
    return ET.fromstring(adb('shell','cat','/sdcard/office.xml').decode()).iter('node')
def find(text, timeout=30, enabled=False):
    end=time.time()+timeout
    while time.time()<end:
        try:
            for node in nodes():
                if text==node.get('text') and (not enabled or node.get('enabled')=='true'):
                    return node
        except Exception: pass
        time.sleep(1)
    raise RuntimeError('UI text not found: '+text)
def tap(text):
    a=list(map(int,re.findall(r'\d+',find(text).get('bounds'))))
    adb('shell','input','tap',str((a[0]+a[2])//2),str((a[1]+a[3])//2))
    time.sleep(1)
def shot(name):
    time.sleep(2)
    pathlib.Path('screenshots/'+name+'.png').write_bytes(adb('exec-out','screencap','-p'))
find('Инструкции');shot('01_home');tap('Инструкции')
find('Пример Word.docx');shot('02_catalog')
for title,screenshot,expected in [('Пример Word.docx','03_word','Контрольный пример'),('Пример Excel.xlsx','04_excel','План работ'),('Старый Excel.xls',None,'Тест XLS'),('Старый Word.doc',None,None)]:
    tap(title);find('В PDF',timeout=40,enabled=True)
    if expected: find(expected,timeout=20)
    if screenshot: shot(screenshot)
    print('VIEW PASS',title,flush=True)
    adb('shell','input','keyevent','4');find(title)
tap('Пример Word.docx');find('В PDF',enabled=True);tap('В PDF');find('Сохранение в PDF');shot('05_export')
print('PDF confirmation displayed; output file not tested',flush=True)
PY
