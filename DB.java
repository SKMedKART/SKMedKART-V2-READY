package com.skmedkart.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DB extends SQLiteOpenHelper {
    private static final int DB_VERSION = 3;

    public static class Medicine {
        public long id; public String name; public double price; public int stock; public String expiry;
        Medicine(long id, String name, double price, int stock, String expiry) {
            this.id=id; this.name=name; this.price=price; this.stock=stock; this.expiry=expiry;
        }
    }

    public static class BillItem {
        public long medicineId; public String name; public double price; public int qty;
        public BillItem(long medicineId, String name, double price, int qty) {
            this.medicineId=medicineId; this.name=name; this.price=price; this.qty=qty;
        }
        public double amount() { return price * qty; }
    }

    public DB(Context context) { super(context, "skmedkart.db", null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE customers(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,phone TEXT,notes TEXT)");
        db.execSQL("CREATE TABLE medicines(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL,price REAL NOT NULL,stock INTEGER NOT NULL,expiry TEXT)");
        db.execSQL("CREATE TABLE bills(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_no TEXT UNIQUE,customer TEXT,phone TEXT,subtotal REAL NOT NULL DEFAULT 0,discount REAL NOT NULL DEFAULT 0,gst_percent REAL NOT NULL DEFAULT 0,gst_amount REAL NOT NULL DEFAULT 0,total REAL NOT NULL DEFAULT 0,created TEXT NOT NULL)");
        db.execSQL("CREATE TABLE bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS bill_items(id INTEGER PRIMARY KEY AUTOINCREMENT,bill_id INTEGER NOT NULL,medicine_id INTEGER NOT NULL,medicine_name TEXT NOT NULL,price REAL NOT NULL,qty INTEGER NOT NULL,amount REAL NOT NULL)");
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db,"bills","bill_no","TEXT");
            addColumnIfMissing(db,"bills","subtotal","REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db,"bills","discount","REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db,"bills","gst_percent","REAL NOT NULL DEFAULT 0");
            addColumnIfMissing(db,"bills","gst_amount","REAL NOT NULL DEFAULT 0");
            db.execSQL("UPDATE bills SET subtotal=total WHERE subtotal=0");
            db.execSQL("UPDATE bills SET bill_no='SKM-' || printf('%06d',id) WHERE bill_no IS NULL OR bill_no='' ");
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db,String table,String column,String definition) {
        Cursor c=null; boolean exists=false;
        try {
            c=db.rawQuery("PRAGMA table_info("+table+")",null);
            while(c.moveToNext()) if(column.equalsIgnoreCase(c.getString(1))) { exists=true; break; }
            if(!exists) db.execSQL("ALTER TABLE "+table+" ADD COLUMN "+column+" "+definition);
        } finally { if(c!=null)c.close(); }
    }

    public long addCustomer(String name,String phone,String notes) {
        ContentValues v=new ContentValues(); v.put("name",name); v.put("phone",phone); v.put("notes",notes);
        return getWritableDatabase().insertOrThrow("customers",null,v);
    }

    public long addMedicine(String name,double price,int stock,String expiry) {
        ContentValues v=new ContentValues(); v.put("name",name); v.put("price",price); v.put("stock",stock); v.put("expiry",expiry);
        return getWritableDatabase().insertOrThrow("medicines",null,v);
    }

    public long addBillWithItems(String customer,String phone,double total,String created,ArrayList<BillItem> items) {
        return addBillWithItems(customer,phone,total,0,0,0,total,created,items);
    }

    public long addBillWithItems(String customer,String phone,double subtotal,double discount,double gstPercent,double gstAmount,double total,String created,ArrayList<BillItem> items) {
        SQLiteDatabase db=getWritableDatabase();
        if(items==null || items.isEmpty()) throw new IllegalStateException("Bill must contain at least one medicine");
        if(discount<0 || gstPercent<0 || gstAmount<0) throw new IllegalStateException("Invalid discount/GST");
        Map<Long,Integer> required=new HashMap<>();
        for(BillItem item:items) {
            if(item.qty<=0) throw new IllegalStateException("Invalid quantity for "+item.name);
            Integer old=required.get(item.medicineId); required.put(item.medicineId,(old==null?0:old)+item.qty);
        }
        db.beginTransaction();
        try {
            for(Map.Entry<Long,Integer> e:required.entrySet()) {
                Cursor c=db.rawQuery("SELECT name,stock FROM medicines WHERE id=?",new String[]{String.valueOf(e.getKey())});
                if(!c.moveToFirst()){c.close();throw new IllegalStateException("Medicine no longer exists");}
                String name=c.getString(0); int stock=c.getInt(1); c.close();
                if(e.getValue()>stock) throw new IllegalStateException("Insufficient stock: "+name+" (available "+stock+")");
            }
            ContentValues bill=new ContentValues();
            bill.put("customer",customer); bill.put("phone",phone); bill.put("subtotal",subtotal); bill.put("discount",discount);
            bill.put("gst_percent",gstPercent); bill.put("gst_amount",gstAmount); bill.put("total",total); bill.put("created",created);
            long id=db.insertOrThrow("bills",null,bill);
            ContentValues no=new ContentValues(); no.put("bill_no",String.format("SKM-%06d",id)); db.update("bills",no,"id=?",new String[]{String.valueOf(id)});
            for(BillItem item:items) {
                ContentValues line=new ContentValues(); line.put("bill_id",id); line.put("medicine_id",item.medicineId); line.put("medicine_name",item.name);
                line.put("price",item.price); line.put("qty",item.qty); line.put("amount",item.amount());
                db.insertOrThrow("bill_items",null,line);
                db.execSQL("UPDATE medicines SET stock=stock-? WHERE id=? AND stock>=?",new Object[]{item.qty,item.medicineId,item.qty});
            }
            db.setTransactionSuccessful(); return id;
        } finally { db.endTransaction(); }
    }

    public ArrayList<Medicine> medicineList(){
        ArrayList<Medicine> list=new ArrayList<>(); Cursor c=getReadableDatabase().rawQuery("SELECT id,name,price,stock,expiry FROM medicines ORDER BY name COLLATE NOCASE",null);
        while(c.moveToNext()) list.add(new Medicine(c.getLong(0),c.getString(1),c.getDouble(2),c.getInt(3),c.getString(4))); c.close(); return list;
    }
    public Cursor customers(){return getReadableDatabase().rawQuery("SELECT id,name,phone,notes FROM customers ORDER BY id DESC",null);}
    public Cursor medicines(){return getReadableDatabase().rawQuery("SELECT id,name,price,stock,expiry FROM medicines ORDER BY id DESC",null);}
    public Cursor bills(){return getReadableDatabase().rawQuery("SELECT id,bill_no,customer,phone,subtotal,discount,gst_percent,gst_amount,total,created FROM bills ORDER BY id DESC",null);}
    public Cursor getBill(long id){return getReadableDatabase().rawQuery("SELECT id,bill_no,customer,phone,subtotal,discount,gst_percent,gst_amount,total,created FROM bills WHERE id=?",new String[]{String.valueOf(id)});}
    public Cursor getBillItems(long id){return getReadableDatabase().rawQuery("SELECT id,bill_id,medicine_id,medicine_name,price,qty,amount FROM bill_items WHERE bill_id=? ORDER BY id ASC",new String[]{String.valueOf(id)});}
    public double salesBetween(String start,String end){
        Cursor c=getReadableDatabase().rawQuery("SELECT COALESCE(SUM(total),0) FROM bills WHERE created>=? AND created<=?",new String[]{start,end}); double v=0; if(c.moveToFirst())v=c.getDouble(0); c.close(); return v;
    }
    public int countLowStock(int threshold){Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM medicines WHERE stock<=?",new String[]{String.valueOf(threshold)});int v=0;if(c.moveToFirst())v=c.getInt(0);c.close();return v;}
}
