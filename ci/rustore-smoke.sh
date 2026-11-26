#!/usr/bin/env bash
set -euo pipefail
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
python3 - <<'PY'
import json, xml.etree.ElementTree as ET
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
pdfmetrics.registerFont(TTFont('DejaVu','/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'))
c=canvas.Canvas('fixtures/demo.pdf',pagesize=(595,842))
for page in range(1,4):
    c.setFillColorRGB(0.04,0.47,0.59);c.rect(0,770,595,72,fill=1,stroke=0)
    c.setFillColorRGB(1,1,1);c.setFont('DejaVu',21);c.drawString(32,799,'Демонстрационное руководство')
    c.setFillColorRGB(0.1,0.17,0.21);c.setFont('DejaVu',16);c.drawString(32,732,'Раздел '+str(page)+'. Работа с документами')
    c.setFont('DejaVu',12)
    for i,s in enumerate(['Этот пример предназначен для демонстрации приложения.','Документы сохраняются в локальной библиотеке.','Организуйте файлы по папкам и разделам.','Добавляйте заметки и находите нужные материалы.','Используйте прокрутку и увеличение страниц PDF.']):
        c.drawString(32,695-i*27,s)
    for i in range(5):
        y=490-i*46
        c.setStrokeColorRGB(.69,.77,.8);c.rect(32,y,531,46)
        c.setFillColorRGB(.1,.17,.21);c.drawString(44,y+16,['Проверка документа','Сортировка по папкам','Добавление заметки','Просмотр PDF','Отправка оригинала'][i])
        c.drawString(440,y+16,'Пункт '+str(i+1))
    c.drawString(32,42,'Учебный пример   •   Страница '+str(page)+' из 3')
    c.showPage()
c.save()
folders=[dict(id='manuals',name='Руководства',section='instructions'),dict(id='training',name='Учебные материалы',section='instructions'),dict(id='checklists',name='Чек-листы',section='instructions')]
docs=[dict(id='pdf',stored='demo.pdf',title='Руководство по работе.pdf',mime='application/pdf',section='instructions',folderId='manuals',note='Демонстрационный документ • 3 страницы'),
      dict(id='word',stored='demo.docx',title='Рабочая инструкция.docx',mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document',section='instructions',folderId='manuals',note='Порядок выполнения работ'),
      dict(id='excel',stored='demo.xlsx',title='План работ.xlsx',mime='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',section='instructions',folderId='manuals',note='Таблица для планирования'),
      dict(id='xls',stored='demo.xls',title='Журнал учёта.xls',mime='application/vnd.ms-excel',section='instructions',folderId=None,note='Демонстрационный файл'),
      dict(id='form',stored='form.docx',title='Бланк проверки.docx',mime='application/vnd.openxmlformats-officedocument.wordprocessingml.document',section='forms',folderId=None,note='Шаблон для заполнения')]
root=ET.Element('map')
ET.SubElement(root,'string',name='docs').text=json.dumps(docs,ensure_ascii=False)
ET.SubElement(root,'string',name='folders').text=json.dumps(folders,ensure_ascii=False)
ET.SubElement(root,'int',name='theme',value='1')
ET.ElementTree(root).write('fixtures/catalog.xml',encoding='utf-8',xml_declaration=True)
PY
cp fixtures/demo.docx fixtures/form.docx
adb shell am force-stop ru.amafor.offlinedocs.stable
adb shell run-as ru.amafor.offlinedocs.stable mkdir -p files/docs shared_prefs
for f in demo.pdf demo.docx demo.xlsx demo.xls form.docx; do
  adb shell -T "run-as ru.amafor.offlinedocs.stable sh -c 'cat > files/docs/$f'" < "fixtures/$f"
done
adb shell -T "run-as ru.amafor.offlinedocs.stable sh -c 'cat > shared_prefs/catalog.xml'" < fixtures/catalog.xml
python3 - <<'PY'
import subprocess,time,re,xml.etree.ElementTree as ET,pathlib
P='ru.amafor.offlinedocs.stable'
def adb(*args):return subprocess.check_output(['adb',*args],stderr=subprocess.STDOUT)
def nodes():
    adb('shell','uiautomator','dump','/sdcard/store.xml')
    return list(ET.fromstring(adb('shell','cat','/sdcard/store.xml').decode()).iter('node'))
def find(label,timeout=20,enabled=False):
    end=time.time()+timeout
    while time.time()<end:
        try:
            for n in nodes():
                if label.casefold() in (n.get('text','').casefold(),n.get('content-desc','').casefold()) and (not enabled or n.get('enabled')=='true'):return n
        except Exception:pass
        time.sleep(0.5)
    raise RuntimeError('UI missing: '+label)
def tap(label,long=False):
    n=find(label);a=list(map(int,re.findall(r'\d+',n.get('bounds'))));x=str((a[0]+a[2])//2);y=str((a[1]+a[3])//2)
    adb('shell','input',*(['swipe',x,y,x,y,'850'] if long else ['tap',x,y]));time.sleep(0.8)
def shot(folder,name):
    time.sleep(1)
    path=pathlib.Path('screenshots',folder,name+'.png');path.parent.mkdir(parents=True,exist_ok=True)
    path.write_bytes(adb('exec-out','screencap','-p'));print('CAPTURE',path,flush=True)
def back():adb('shell','input','keyevent','4');time.sleep(.8)
for kind,size,density in [('phone','1080x1920','360'),('tablet','1440x2560','240')]:
    adb('shell','am','force-stop',P);adb('shell','wm','size',size);adb('shell','wm','density',density);time.sleep(2)
    adb('shell','am','start','-n',P+'/ru.amafor.offlinedocs.MainActivity');find('Инструкции');shot(kind,'01_home')
    tap('Инструкции');find('Руководства');shot(kind,'02_folders')
    tap('Руководства');find('Руководство по работе.pdf');shot(kind,'03_documents')
    tap('Руководство по работе.pdf');find('Сбросить масштаб');shot(kind,'04_pdf')
    back();find('Руководство по работе.pdf')
    tap('Руководство по работе.pdf',True);find('Заметка');tap('Заметка');find('Сохранить');shot(kind,'05_note')
    back();time.sleep(1)
    try:
        find('Рабочая инструкция.docx');tap('Рабочая инструкция.docx');find('В PDF',timeout=30,enabled=True);shot(kind,'06_word');back()
        find('План работ.xlsx');tap('План работ.xlsx');find('В PDF',timeout=30,enabled=True);shot(kind,'07_excel');back()
    except Exception as e:print('Optional Office capture not verified:',e,flush=True)
PY
