package sample.xenlon;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;

public class Importer {

    static final int MAX_COL_PER_ROW = 64;

    static final int MAX_ROW = 1024;

    public static void main(String[] args) throws Exception {

        String currentPath = getCurrentPath();

        sysinfo("current Path :" + currentPath);

        Importer importer = new Importer();

        importer.run(currentPath + "\\test.xls");

    }

    public void run(String fileName) throws Exception {

        Connection dbcnn = getDBConnection();

        if (dbcnn == null) {
            syserr("fail connect.");
            return;
        }

        sysinfo("success connect.");

        HSSFWorkbook workbook = getWorkbook(fileName);

        if (workbook == null) {
            syserr("fail get Workbook : " + fileName);
            return;
        }

        Statement stmt = dbcnn.createStatement();

        procWorkbook(workbook, stmt);

        stmt.close();

        dbcnn.close();

    }

    protected Connection getDBConnection() throws Exception {

        ResourceBundle bundle = ResourceBundle.getBundle("lw_xenlon");

        String jdbcDriver = bundle.getString("DB_DRV");

        Class.forName(jdbcDriver);

        String dbUrl = bundle.getString("DB_URI");
        String dbUid = bundle.getString("DB_UID");
        String dbPwd = bundle.getString("DB_PWD");

        Connection dbcnn = DriverManager.getConnection(dbUrl, dbUid, dbPwd);

        return dbcnn;

    }

    protected HSSFWorkbook getWorkbook(String fileName) throws IOException {

        FileInputStream fis = null;
        HSSFWorkbook workbook = null;

        try {
            fis = new FileInputStream(fileName);
        } catch (Exception ex) {
            syserr(ex.toString());
            return null;
        }

        try {
            POIFSFileSystem fs = new POIFSFileSystem(fis);
            workbook = new HSSFWorkbook(fs);
        } catch (Exception ex) {
            syserr(ex.toString());
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                syserr(e.toString());
            }
        }
        return workbook;

    }

    protected void procWorkbook(HSSFWorkbook workbook, Statement stmt) {

        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {

            HSSFSheet sheet = workbook.getSheetAt(i);

            procSheet(sheet, stmt);

        }

    }

    protected void procSheet(HSSFSheet sheet, Statement stmt) {

        String sheetName = sheet.getSheetName();

        executeTruncateSql(stmt, sheetName);

        List<List<String>> values = getSheetData(sheet);

        printValues(values);

        executeInsertSql(stmt, sheetName, values);

    }

    protected void executeTruncateSql(Statement stmt, String tableName) {

        try {

            String sql = "TRUNCATE TABLE " + tableName + ";";

            sysinfo(sql);

            stmt.executeUpdate(sql);

        } catch (Exception ex) {
            syserr(ex.toString());
        }

    }

    protected void executeInsertSql(Statement stmt, String tableName, List<List<String>> values) {

        List<String> header = values.get(0);
        List<String> types = values.get(1);

        values.remove(0);
        values.remove(0);

        for (List<String> value : values) {

            executeInsertSql(stmt, tableName, header, types, value);

        }

    }

    protected void executeInsertSql(Statement stmt, String tableName, List<String> columns,
            List<String> types, List<String> values) {

        StringBuilder sql = new StringBuilder();

        sql.append("INSERT INTO ").append(tableName).append(" (");
        sql.append(stringList2String(columns));
        sql.append(") VALUES (");
        sql.append(stringList2String(types, values));
        sql.append(");");

        sysinfo(sql.toString());

        try {

            stmt.executeUpdate(sql.toString());

        } catch (Exception ex) {
            syserr(ex.toString());
        }

    }

    protected String stringList2String(List<String> values) {

        StringBuilder strb = new StringBuilder();

        for (String value : values) {

            strb.append(value).append(",");

        }

        String str = strb.toString();

        return str.substring(0, str.lastIndexOf(","));

    }

    protected String stringList2String(List<String> types, List<String> values) {

        StringBuilder strb = new StringBuilder();

        for (int i = 0; i < types.size(); i++) {

            String type = types.get(i);
            String value = values.get(i);

            if ("string".equalsIgnoreCase(type)) {

                strb.append("'").append(value).append("',");

            } else {

                strb.append(value).append(",");
            }

        }

        String str = strb.toString();

        return str.substring(0, str.lastIndexOf(","));

    }

    protected List<List<String>> getSheetData(HSSFSheet sheet) {

        List<String> header = new ArrayList<String>();
        List<List<String>> values = new ArrayList<List<String>>();

        HSSFRow row = sheet.getRow(0);

        if (row == null) {
            return values;
        }

        header = getRowData(row, MAX_COL_PER_ROW);

        values.add(header);

        int rowNumber = 1;

        for (; rowNumber < MAX_ROW; rowNumber++) {

            row = sheet.getRow(rowNumber);

            if (row == null) {
                break;
            }

            List<String> list = getRowData(row, header.size());

            if (list.size() == 0) {
                break;
            }

            values.add(list);

        }

        if (rowNumber == MAX_ROW) {
            syserr("line Number over " + rowNumber);
        }

        return values;

    }

    public List<String> getRowData(HSSFRow row, int size) {

        List<String> list = new ArrayList<String>();

        HSSFCell cell = row.getCell(0);

        String cellString = getCellData(cell);

        if ("EOF".equalsIgnoreCase(cellString)) {
            return list;
        }

        list.add(cellString);

        for (int i = 1; i < size; i++) {

            cell = row.getCell(i);
            cellString = getCellData(cell);

            if ("EOF".equalsIgnoreCase(cellString)) {
                break;

            }
            list.add(cellString);

        }

        return list;

    }

    public String getCellData(HSSFCell cell) {

        if (cell == null) {
            return new String("");
        }

        String str = cell.getStringCellValue();

        if (str == null) {
            str = new String("");
        }

        return str;

    }

    protected void printValues(List<List<String>> values) {

        for (List<String> value : values) {

            printList(value);

        }

    }

    protected void printList(List<String> list) {

        for (String str : list) {

            sysprint(str);
            sysprint(",");

        }
        sysprint("End");
        sysprint("\n");

    }

    protected static void sysinfo(String str) {

        sysprintln(str);

    }

    protected static void syswarn(String str) {

        sysprintln(str);

    }

    protected static void sysdebug(String str) {

        sysprintln(str);

    }

    protected static void syserr(String str) {

        sysprintln(str);

    }

    protected static void sysprintln(String str) {

        System.out.println(str);

    }

    protected static void sysprint(String str) {

        System.out.print(str);

    }

    protected static String getCurrentPath() {

        File file = new File("");

        return file.getAbsolutePath();
    }

}
