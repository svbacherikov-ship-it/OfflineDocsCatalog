package ru.amafor.offlinedocs;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.SAXException;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;

public final class OfficePreview {
    private static final long FILE_LIMIT = 25L*1024*1024;
    private static final long XML_LIMIT = 16L*1024*1024;
    private static final int MAX_ROWS=10000, MAX_COLS=256, MAX_CELLS=100000;
    private static final int MAX_HTML=12000000;
    private OfficePreview() {}
    public static boolean supports(String name, String mime) {
        String n=name==null?"":name.toLowerCase(Locale.ROOT);
        String m=mime==null?"":mime.toLowerCase(Locale.ROOT);
        return n.endsWith(".doc")||n.endsWith(".docx")||n.endsWith(".xls")||n.endsWith(".xlsx")
            ||m.equals("application/msword")||m.equals("application/vnd.ms-excel")
            ||m.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            ||m.equals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
    public static String read(File f,String title,String mime) throws Exception {
        if(f.length()>FILE_LIMIT) throw new IOException("Файл больше 25 МБ. Используйте внешний редактор.");
        String n=f.getName().toLowerCase(Locale.ROOT), m=mime==null?"":mime;
        String t=title==null?"Документ":title;
        StringBuilder b=new StringBuilder("<!DOCTYPE html><html lang='ru'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><meta http-equiv='Content-Security-Policy' content=\"default-src 'none'; style-src 'unsafe-inline'\"><style>body{font-family:sans-serif;color:#20303a;background:white;margin:18px;line-height:1.55}h1{font-size:20px;overflow-wrap:anywhere}h2{font-size:17px;margin-top:24px}p{white-space:pre-wrap;overflow-wrap:anywhere;margin:0 0 9px}.notice{background:#edf5f7;border-left:3px solid #0b7896;padding:8px;font-size:12px}table{border-collapse:collapse;font-size:13px;margin:12px 0;min-width:100%}td,th{border:1px solid #9faab0;padding:5px 8px;white-space:pre-wrap;overflow-wrap:anywhere;vertical-align:top}th{background:#e8eff2}td{min-width:48px}section{margin-bottom:20px}@media print{body{margin:0}table{width:100%;table-layout:fixed;font-size:9pt}td,th{min-width:0;padding:3px}tr{break-inside:avoid}thead{display:table-header-group}h2{break-after:avoid}.notice{font-size:9pt}}</style></head><body>");
        b.append("<h1>").append(escape(t)).append("</h1><p class='notice'>Упрощённое представление. Изображения, схемы, диаграммы, колонтитулы и точная вёрстка могут отсутствовать; формулы не пересчитываются. PDF отражает этот просмотр, а не оригинальную печатную форму.</p>");
        if(n.endsWith(".docx")||m.contains("wordprocessingml")) {
            try(ZipFile z=new ZipFile(f)){Document d=xml(z,"word/document.xml",true);NodeList bodies=d.getElementsByTagNameNS("*","body"); if(bodies.getLength()==0)throw new IOException("Нет основного текста");wordBody(b,bodies.item(0));}
        } else if(n.endsWith(".xlsx")||m.contains("spreadsheetml")) {
            try(ZipFile z=new ZipFile(f)){xlsx(b,z);}
        } else if(n.endsWith(".doc")||m.equals("application/msword")) {
            try(InputStream in=new BufferedInputStream(new FileInputStream(f)); HWPFDocument doc=new HWPFDocument(in); WordExtractor ex=new WordExtractor(doc)){
                for(String p:ex.getParagraphText()){b.append("<p>").append(escape(p.replace('\u0007',' '))).append("</p>");guard(b);}
            }
        } else if(n.endsWith(".xls")||m.equals("application/vnd.ms-excel")) {
            try(InputStream in=new BufferedInputStream(new FileInputStream(f)); HSSFWorkbook wb=new HSSFWorkbook(in)){xls(b,wb);}
        } else throw new IOException("Неизвестный формат Office");
        guard(b); return b.append("</body></html>").toString();
    }
    private static String escape(String s){
        return s==null?"":s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
    }
    private static void guard(StringBuilder b)throws IOException{if(b.length()>MAX_HTML)throw new IOException("Слишком большой объём содержимого для встроенного просмотра.");}
    private static Document xml(ZipFile z,String name,boolean required)throws Exception {
        ZipEntry e=z.getEntry(name);if(e==null){if(required)throw new IOException("Не найден "+name);return null;}
        if(e.getSize()>XML_LIMIT)throw new IOException("Слишком большой XML");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=z.getInputStream(e)){byte[] buf=new byte[8192];int r;while((r=in.read(buf))!=-1){if(out.size()+r>XML_LIMIT)throw new IOException("Слишком большой XML");out.write(buf,0,r);}}
        DocumentBuilderFactory fac=DocumentBuilderFactory.newInstance();fac.setNamespaceAware(true);
        fac.setFeature("http://xml.org/sax/features/external-general-entities",false);
        fac.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        fac.setExpandEntityReferences(false);
        DocumentBuilder builder=fac.newDocumentBuilder();
        builder.setEntityResolver((pub,sys)->{throw new SAXException("Внешние XML-сущности запрещены");});
        Document doc=builder.parse(new ByteArrayInputStream(out.toByteArray()));
        if(doc.getDoctype()!=null)throw new IOException("DTD запрещён");
        return doc;
    }
    private static String tag(Node n){return n.getLocalName()==null?n.getNodeName():n.getLocalName();}
    private static List<Element> children(Node n,String type){List<Element> out=new ArrayList<>();for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())if(c instanceof Element&&(type==null||tag(c).equals(type)))out.add((Element)c);return out;}
    private static String text(Element e,String name){NodeList l=e.getElementsByTagNameNS("*",name);StringBuilder b=new StringBuilder();for(int i=0;i<l.getLength();i++)b.append(l.item(i).getTextContent());return b.toString();}
    private static void wordBody(StringBuilder b,Node n)throws IOException{
        for(Element e:children(n,null)){
            if(tag(e).equals("p")){b.append("<p>");for(Element run:children(e,null)){if(tag(run).equals("r")||tag(run).equals("hyperlink"))wordRun(b,run);}b.append("</p>");}
            else if(tag(e).equals("tbl")){b.append("<table>");for(Element row:children(e,"tr")){b.append("<tr>");for(Element cell:children(row,"tc")){b.append("<td>");wordBody(b,cell);b.append("</td>");}b.append("</tr>");}b.append("</table>");}
            guard(b);
        }
    }
    private static void wordRun(StringBuilder b,Element e){
        if(tag(e).equals("hyperlink")){for(Element r:children(e,"r"))wordRun(b,r);return;}
        boolean bold=e.getElementsByTagNameNS("*","b").getLength()>0, italic=e.getElementsByTagNameNS("*","i").getLength()>0;
        if(bold)b.append("<b>");if(italic)b.append("<i>");
        for(Element c:children(e,null)){switch(tag(c)){case "t":b.append(escape(c.getTextContent()));break;case "tab":b.append("    ");break;case "br":case "cr":b.append("<br>");break;default:break;}}
        if(italic)b.append("</i>");if(bold)b.append("</b>");
    }
    private static int col(String ref){int k=0,i=0;while(i<ref.length()&&ref.charAt(i)>='A'&&ref.charAt(i)<='Z'){k=k*26+ref.charAt(i++)-'A'+1;if(k>MAX_COLS)return MAX_COLS;}return i==0?-1:k-1;}
    private static String column(int c){return CellReference.convertNumToColString(c);}
    private static void startTable(StringBuilder b,String name,int cols){b.append("<section><h2>").append(escape(name)).append("</h2><table><thead><tr><th>№</th>");for(int c=0;c<cols;c++)b.append("<th>").append(column(c)).append("</th>");b.append("</tr></thead><tbody>");}
    private static void row(StringBuilder b,int row,String[] values)throws IOException{b.append("<tr><th>").append(row).append("</th>");for(String v:values)b.append("<td>").append(escape(v)).append("</td>");b.append("</tr>");guard(b);}
    private static String cellValue(Element e,List<String> ss,DataFormatter fmt,List<Integer> styles,Map<Integer,String> formats){
        String typ=e.getAttribute("t"), v=text(e,"v");
        if("inlineStr".equals(typ))return text(e,"t");
        if("s".equals(typ)){try{return ss.get(Integer.parseInt(v));}catch(Exception err){return "[не удалось прочитать строку]";}}
        if("b".equals(typ))return "1".equals(v)?"TRUE":"FALSE";
        if(e.getElementsByTagNameNS("*","f").getLength()>0&&v.isEmpty())return "[нет сохранённого результата формулы]";
        if((typ.isEmpty()||"n".equals(typ))&&!v.isEmpty()){
            try{int style=e.hasAttribute("s")?Integer.parseInt(e.getAttribute("s")):0;int id=styles.get(style);String pattern=formats.get(id);if(pattern==null)pattern=BuiltinFormats.getBuiltinFormat(id);if(pattern!=null)return fmt.formatRawCellContents(Double.parseDouble(v),id,pattern);}catch(Exception ignored){}
        }
        return v;
    }
    private static void xlsx(StringBuilder b,ZipFile z)throws Exception{
        List<String> ss=new ArrayList<>();Document strings=xml(z,"xl/sharedStrings.xml",false);
        if(strings!=null){NodeList l=strings.getElementsByTagNameNS("*","si");for(int i=0;i<l.getLength();i++)ss.add(text((Element)l.item(i),"t"));}
        List<Integer> styles=new ArrayList<>();Map<Integer,String> formats=new HashMap<>();
        Document st=xml(z,"xl/styles.xml",false);if(st!=null){NodeList l=st.getElementsByTagNameNS("*","numFmt");for(int i=0;i<l.getLength();i++){Element e=(Element)l.item(i);formats.put(Integer.parseInt(e.getAttribute("numFmtId")),e.getAttribute("formatCode"));}NodeList x=st.getElementsByTagNameNS("*","cellXfs");if(x.getLength()>0)for(Element e:children(x.item(0),"xf"))styles.add(Integer.parseInt(e.getAttribute("numFmtId")));}
        DataFormatter fmt=new DataFormatter(Locale.getDefault());
        Document workbook=xml(z,"xl/workbook.xml",true), rel=xml(z,"xl/_rels/workbook.xml.rels",true);
        Map<String,String> targets=new HashMap<>();NodeList rr=rel.getElementsByTagNameNS("*","Relationship");
        for(int i=0;i<rr.getLength();i++){Element e=(Element)rr.item(i);if(!"External".equals(e.getAttribute("TargetMode"))&&e.getAttribute("Type").endsWith("/worksheet"))targets.put(e.getAttribute("Id"),e.getAttribute("Target"));}
        NodeList sheets=workbook.getElementsByTagNameNS("*","sheet");int count=0;
        for(int s=0;s<sheets.getLength();s++){
            Element sh=(Element)sheets.item(s);String rid=sh.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships","id");if(rid.isEmpty())rid=sh.getAttribute("r:id");String target=targets.get(rid);if(target==null)throw new IOException("Неподдерживаемый тип листа");
            String path=target.startsWith("/")?target.substring(1):"xl/"+target;if(!path.startsWith("xl/")||path.contains("..")||path.contains(":"))throw new IOException("Недопустимый путь листа");
            Document sd=xml(z,path,true);NodeList rows=sd.getElementsByTagNameNS("*","row");if(rows.getLength()>MAX_ROWS)throw new IOException("Лист содержит более 10000 строк");
            int cols=1;
            for(int i=0;i<rows.getLength();i++){int next=0;for(Element e:children(rows.item(i),"c")){int c=col(e.getAttribute("r"));if(c<0)c=next;next=c+1;cols=Math.max(cols,next);}}
            if(cols>MAX_COLS||(long)cols*rows.getLength()+count>MAX_CELLS)throw new IOException("Таблица слишком велика для просмотра");
            count+=cols*rows.getLength();startTable(b,sh.getAttribute("name"),cols);
            for(int i=0;i<rows.getLength();i++){Element re=(Element)rows.item(i);int number=i+1;try{number=Integer.parseInt(re.getAttribute("r"));}catch(Exception ignored){}String[] vals=new String[cols];int next=0;for(Element e:children(re,"c")){int c=col(e.getAttribute("r"));if(c<0)c=next;next=c+1;vals[c]=cellValue(e,ss,fmt,styles,formats);}row(b,number,vals);}
            b.append("</tbody></table></section>");
        }
    }
    private static void xls(StringBuilder b,HSSFWorkbook wb)throws Exception{
        DataFormatter fmt=new DataFormatter(Locale.getDefault());int total=0;
        for(Sheet sheet:wb){int cols=1, rows=0;for(Row r:sheet){cols=Math.max(cols,r.getLastCellNum());rows++;}if(cols>MAX_COLS||rows>MAX_ROWS||(long)cols*rows+total>MAX_CELLS)throw new IOException("Таблица слишком велика для просмотра");total+=cols*rows;startTable(b,sheet.getSheetName(),cols);
            for(Row r:sheet){String[] vals=new String[cols];for(Cell c:r){String v;if(c.getCellType()==CellType.FORMULA){switch(c.getCachedFormulaResultType()){case NUMERIC:v=fmt.formatRawCellContents(c.getNumericCellValue(),c.getCellStyle().getDataFormat(),c.getCellStyle().getDataFormatString());break;case STRING:v=c.getStringCellValue();break;case BOOLEAN:v=String.valueOf(c.getBooleanCellValue());break;case ERROR:v=FormulaError.forInt(c.getErrorCellValue()).getString();break;default:v="";}}else v=fmt.formatCellValue(c);vals[c.getColumnIndex()]=v;}row(b,r.getRowNum()+1,vals);}
            b.append("</tbody></table></section>");
        }
    }
}
