package com.skmedkart.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {
    DB db;
    LinearLayout root;
    ArrayList<DB.BillItem> cart = new ArrayList<>();
    TextView cartText, reportText;

    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    TextView tv(String s,int size){ TextView t=new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(dp(8),dp(8),dp(8),dp(8)); return t; }
    EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setPadding(dp(10),dp(8),dp(10),dp(8)); return e; }

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        db=new DB(this);
        build();
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 9);
    }

    void build(){
        ScrollView scroll=new ScrollView(this);
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(12),dp(12),dp(12),dp(24));
        scroll.addView(root); setContentView(scroll);

        TextView title=tv("SKMedKART V2",26); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD); root.addView(title);
        root.addView(tv("Sri Krishna Medicals • Pennagaram",15));

        Button add=btn("➕ Add Medicine"); add.setOnClickListener(v->addMedicineDialog()); root.addView(add);
        Button sell=btn("🧾 New Bill"); sell.setOnClickListener(v->billDialog()); root.addView(sell);
        Button stock=btn("📦 Stock / Alerts"); stock.setOnClickListener(v->showStock()); root.addView(stock);
        Button reports=btn("📊 Sales Report"); reports.setOnClickListener(v->showReports()); root.addView(reports);
        Button share=btn("💬 Share WhatsApp Message"); share.setOnClickListener(v->shareWhatsApp()); root.addView(share);

        cartText=tv("Cart: 0 items",16); root.addView(cartText);
        reportText=tv("",16); root.addView(reportText);
    }

    Button btn(String s){ Button b=new Button(this); b.setText(s); b.setAllCaps(false); root.addView(b,new LinearLayout.LayoutParams(-1,dp(52))); return b; }

    void addMedicineDialog(){
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        EditText n=input("Medicine name");
        EditText p=input("Price"); p.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText q=input("Stock quantity"); q.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText e=input("Expiry (YYYY-MM-DD)");
        l.addView(n);l.addView(p);l.addView(q);l.addView(e);
        new android.app.AlertDialog.Builder(this).setTitle("Add Medicine").setView(l)
            .setPositiveButton("Save",(d,w)->{
                try { db.addMedicine(n.getText().toString().trim(),Double.parseDouble(p.getText().toString()),Integer.parseInt(q.getText().toString()),e.getText().toString().trim()); toast("Medicine saved"); }
                catch(Exception x){toast("Invalid details");}
            }).setNegativeButton("Cancel",null).show();
    }

    void billDialog(){
        ArrayList<DB.Medicine> meds=db.getMedicines();
        if(meds.isEmpty()){toast("First add medicines"); return;}
        LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL);
        EditText customer=input("Customer name");
        EditText phone=input("Phone number"); phone.setInputType(InputType.TYPE_CLASS_PHONE);
        l.addView(customer);l.addView(phone);
        Spinner sp=new Spinner(this);
        String[] names=new String[meds.size()];
        for(int i=0;i<meds.size();i++) names[i]=meds.get(i).name+" | ₹"+meds.get(i).price+" | stock "+meds.get(i).stock;
        sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names)); l.addView(sp);
        EditText qty=input("Quantity"); qty.setInputType(InputType.TYPE_CLASS_NUMBER); l.addView(qty);
        EditText disc=input("Discount ₹ (optional)"); disc.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); l.addView(disc);
        EditText gst=input("GST % (optional)"); gst.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); l.addView(gst);
        new android.app.AlertDialog.Builder(this).setTitle("New Bill").setView(l)
            .setPositiveButton("Save Bill",(d,w)->{
                try {
                    DB.Medicine m=meds.get(sp.getSelectedItemPosition());
                    int q=Integer.parseInt(qty.getText().toString());
                    DB.BillItem item=new DB.BillItem(m.id,m.name,m.price,q);
                    ArrayList<DB.BillItem> one=new ArrayList<>(); one.add(item);
                    double sub=m.price*q;
                    double discount=disc.getText().length()==0?0:Double.parseDouble(disc.getText().toString());
                    double gstPct=gst.getText().length()==0?0:Double.parseDouble(gst.getText().toString());
                    double taxable=Math.max(0,sub-discount);
                    double total=taxable+(taxable*gstPct/100.0);
                    long id=db.addBillWithItems(customer.getText().toString(),phone.getText().toString(),total,discount,gstPct,one);
                    cart.add(item); cartText.setText("Last bill saved • ID "+id+" • Total ₹"+String.format("%.2f",total));
                    toast("Bill saved. Stock reduced automatically.");
                } catch(Exception x){ toast(x.getMessage()==null?"Bill failed":x.getMessage()); }
            }).setNegativeButton("Cancel",null).show();
    }

    void showStock(){
        ArrayList<DB.Medicine> all=db.getMedicines();
        StringBuilder s=new StringBuilder();
        s.append("MEDICINE STOCK\n\n");
        for(DB.Medicine m:all) s.append(m.name).append(" — ₹").append(m.price).append(" — Stock: ").append(m.stock).append(" — Exp: ").append(m.expiry).append("\n");
        ArrayList<DB.Medicine> low=db.getLowStock(5);
        s.append("\nLOW STOCK (≤5): ").append(low.size());
        new android.app.AlertDialog.Builder(this).setTitle("Stock & Alerts").setMessage(s.toString()).setPositiveButton("OK",null).show();
    }

    void showReports(){
        String s="Today Sales: ₹"+String.format("%.2f",db.todaySales())+"\n\nThis Month: ₹"+String.format("%.2f",db.monthlySales());
        reportText.setText(s);
        toast("Report updated");
    }

    void shareWhatsApp(){
        String text="SKMedKART - Sri Krishna Medicals\nThank you for your purchase.";
        Intent i=new Intent(Intent.ACTION_SEND); i.setType("text/plain"); i.putExtra(Intent.EXTRA_TEXT,text);
        try { startActivity(Intent.createChooser(i,"Share Bill")); } catch(Exception e){toast("No sharing app found");}
    }

    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
}
