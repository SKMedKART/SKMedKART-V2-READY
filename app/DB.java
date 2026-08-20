package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DB extends SQLiteOpenHelper {
    private static final String DB_NAME = "skmedkart.db";
    private static final int DB_VERSION = 2;

    public static class Medicine {
        public long id;
        public String name;
        public double price;
        public int stock;
        public String expiry;
        public Medicine(long id, String name, double price, int stock, String expiry) {
            this.id=id; this.name=name; this.price=price; this.stock=stock; this.expiry=expiry;
        }
    }

    public static class BillItem {
        public long medicineId;
        public String name;
        public double price;
        public int qty;
        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId=medicineId; this.name=name; this.price=price; this.qty=qty;
        }
        public double amount() { return price * qty; }
    }

    public DB(Context c) { super(c, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE medicines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price REAL NOT NULL DEFAULT 0,stock INTEGER NOT NULL DEFAULT 0,expiry TEXT)");
        db.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_no TEXT,customer TEXT,phone TEXT,total REAL,discount REAL,gst REAL,date TEXT)");
        db.execSQL("CREATE TABLE bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER,medicine_id INTEGER,name TEXT,price REAL,qty INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            try { db.execSQL("ALTER TABLE bills ADD COLUMN discount REAL DEFAULT 0"); } catch(Exception ignored) {}
            try { db.execSQL("ALTER TABLE bills ADD COLUMN gst REAL DEFAULT 0"); } catch(Exception ignored) {}
        }
    }

    public long addMedicine(String name, double price, int stock, String expiry) {
        ContentValues v = new ContentValues();
        v.put("name", name); v.put("price", price); v.put("stock", stock); v.put("expiry", expiry);
        return getWritableDatabase().insert("medicines", null, v);
    }

    public ArrayList<Medicine> getMedicines() {
        ArrayList<Medicine> out = new ArrayList<>();
        Cursor c = getReadableDatabase().query("medicines", null, null, null, null, null, "name ASC");
        while(c.moveToNext()) out.add(new Medicine(c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("name")),
                c.getDouble(c.getColumnIndexOrThrow("price")),
                c.getInt(c.getColumnIndexOrThrow("stock")),
                c.getString(c.getColumnIndexOrThrow("expiry"))));
        c.close();
        return out;
    }

    public ArrayList<Medicine> getLowStock(int limit) {
        ArrayList<Medicine> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT * FROM medicines WHERE stock <= ? ORDER BY stock ASC", new String[]{String.valueOf(limit)});
        while(c.moveToNext()) out.add(new Medicine(c.getLong(0),c.getString(1),c.getDouble(2),c.getInt(3),c.getString(4)));
        c.close();
        return out;
    }

    public long addBillWithItems(String customer, String phone, double total, double discount, double gst, ArrayList<BillItem> items) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (BillItem item : items) {
                Cursor c = db.rawQuery("SELECT stock FROM medicines WHERE id=?", new String[]{String.valueOf(item.medicineId)});
                if (!c.moveToFirst()) { c.close(); throw new IllegalStateException("Medicine not found: " + item.name); }
                int currentStock = c.getInt(0); c.close();
                if (currentStock < item.qty) throw new IllegalStateException("Insufficient stock: " + item.name);
            }

            String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            String billNo = "SK-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());

            ContentValues b = new ContentValues();
            b.put("bill_no", billNo); b.put("customer", customer); b.put("phone", phone);
            b.put("total", total); b.put("discount", discount); b.put("gst", gst); b.put("date", date);
            long billId = db.insertOrThrow("bills", null, b);

            for (BillItem item : items) {
                ContentValues iv = new ContentValues();
                iv.put("bill_id", billId); iv.put("medicine_id", item.medicineId);
                iv.put("name", item.name); iv.put("price", item.price); iv.put("qty", item.qty);
                db.insertOrThrow("bill_items", null, iv);
                db.execSQL("UPDATE medicines SET stock = stock - ? WHERE id=?", new Object[]{item.qty, item.medicineId});
            }
            db.setTransactionSuccessful();
            return billId;
        } finally {
            db.endTransaction();
        }
    }

    public double todaySales() {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM bills WHERE date LIKE ?", new String[]{new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + "%"});
        double v = c.moveToFirst() ? c.getDouble(0) : 0; c.close(); return v;
    }

    public double monthlySales() {
        String month = new SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(new Date());
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM bills WHERE date LIKE ?", new String[]{month + "%"});
        double v = c.moveToFirst() ? c.getDouble(0) : 0; c.close(); return v;
    }
}
