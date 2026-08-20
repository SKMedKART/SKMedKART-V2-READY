package com.skmedkart.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.print.PrintAttributes;
import android.print.PrintManager;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private DB db; private LinearLayout box; private final int PAD=18; private final ArrayList<CartItem> cart=new ArrayList<>();
    private static class CartItem { long medicineId; String name; double price; int qty; CartItem(long i,String n,double p,int q){medicineId=i;name=n;price=p;qty=q;} double amount(){return price*qty;} }

    @Override public void onCreate(Bundle state){super.onCreate(state);db=new DB(this);requestNotificationPermission();home();}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setPadding(PAD,10,PAD,10);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setPadding(PAD,8,PAD,8);box.addView(e);return e;}
    private void page(String title){box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(8,8,8,8);android.widget.ScrollView sc=new android.widget.ScrollView(this);sc.addView(box);setContentView(sc);TextView h=text(title,24);h.setTypeface(Typeface.DEFAULT,Typeface.BOLD);box.addView(h);}
    private void addBack(){Button b=button("← Home");box.addView(b);b.setOnClickListener(v->home());}

    private void home(){cart.clear();page("🏪 Sri Krishna Medicals");box.addView(text("Pennagaram • SKMedKART V2",17));
        Button b=button("🧾 New Bill");box.addView(b);b.setOnClickListener(v->bill());
        b=button("💊 Medicine Stock");box.addView(b);b.setOnClickListener(v->medicines());
        b=button("👤 Customers & Refill Reminders");box.addView(b);b.setOnClickListener(v->customers());
        b=button("📊 Sales & Reports");box.addView(b);b.setOnClickListener(v->reports());
        b=button("⚠ Stock / Expiry Alerts");box.addView(b);b.setOnClickListener(v->alerts());
    }

    private void bill(){page("🧾 New Bill");EditText customer=input("Customer name (optional)");EditText phone=input("Mobile number (optional)");phone.setInputType(2);
        ArrayList<DB.Medicine> meds=db.medicineList();ArrayList<String> labels=new ArrayList<>();for(DB.Medicine m:meds)labels.add(m.name+"  ₹"+money(m.price)+"  [Stock "+m.stock+"]");
        box.addView(text("Select medicine",16));Spinner sp=new Spinner(this);sp.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,labels));box.addView(sp);
        EditText qty=input("Quantity");qty.setInputType(2);Button add=button("➕ ADD ITEM");box.addView(add);LinearLayout cartBox=new LinearLayout(this);cartBox.setOrientation(LinearLayout.VERTICAL);box.addView(cartBox);
        EditText discount=input("Discount amount ₹ (0 if none)");discount.setInputType(8194|2);EditText gst=input("GST % (0 if none)");gst.setInputType(8194|2);TextView summary=text("Subtotal ₹0.00\nDiscount ₹0.00\nGST ₹0.00\nGrand Total ₹0.00",19);box.addView(summary);
        add.setOnClickListener(v->{if(meds.isEmpty()){toast("Add medicines to stock first");return;}int p=sp.getSelectedItemPosition();int q=parseInt(qty.getText().toString());if(p<0||q<=0){toast("Enter a valid quantity");return;}DB.Medicine m=meds.get(p);int already=0;for(CartItem x:cart)if(x.medicineId==m.id)already+=x.qty;if(already+q>m.stock){toast("Only "+(m.stock-already)+" in stock");return;}cart.add(new CartItem(m.id,m.name,m.price,q));qty.setText("");renderCart(cartBox,summary,discount,gst);});
        discount.setOnFocusChangeListener((v,f)->renderCart(cartBox,summary,discount,gst));gst.setOnFocusChangeListener((v,f)->renderCart(cartBox,summary,discount,gst));
        Button save=button("💾 SAVE BILL");box.addView(save);Button clear=button("CLEAR ITEMS");box.addView(clear);addBack();clear.setOnClickListener(v->{cart.clear();renderCart(cartBox,summary,discount,gst);});
        save.setOnClickListener(v->{if(cart.isEmpty()){toast("Add at least one medicine");return;}double sub=subtotal();double dis=Math.max(0,parseDouble(discount.getText().toString()));if(dis>sub)dis=sub;double gp=Math.max(0,parseDouble(gst.getText().toString()));double taxable=sub-dis;double ga=taxable*gp/100.0;double total=taxable+ga;ArrayList<DB.BillItem> items=new ArrayList<>();for(CartItem x:cart)items.add(new DB.BillItem(x.medicineId,x.name,x.price,x.qty));String name=customer.getText().toString().trim();if(name.isEmpty())name="Walk-in Customer";try{String d=now();long id=db.addBillWithItems(name,phone.getText().toString().trim(),sub,dis,gp,ga,total,d,items);if(!phone.getText().toString().trim().isEmpty())scheduleReminder(name,phone.getText().toString().trim());toast("Saved "+billNo(id)+" • Stock updated");billActions(id); }catch(Exception e){toast(e.getMessage()==null?"Could not save bill":e.getMessage());}});
    }

    private void renderCart(LinearLayout cb,TextView summary,EditText dis,EditText gst){cb.removeAllViews();double sub=0;for(int i=0;i<cart.size();i++){CartItem x=cart.get(i);sub+=x.amount();LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.addView(text(x.name+" × "+x.qty+"  ₹"+money(x.amount()),16),new LinearLayout.LayoutParams(0,-2,1));Button rm=button("X");row.addView(rm);int idx=i;rm.setOnClickListener(v->{cart.remove(idx);renderCart(cb,summary,dis,gst);});cb.addView(row);}double d=Math.min(Math.max(0,parseDouble(dis.getText().toString())),sub);double gp=Math.max(0,parseDouble(gst.getText().toString()));double ga=(sub-d)*gp/100;summary.setText("Subtotal ₹"+money(sub)+"\nDiscount ₹"+money(d)+"\nGST ("+money(gp)+"%) ₹"+money(ga)+"\nGrand Total ₹"+money(sub-d+ga));}
    private double subtotal(){double s=0;for(CartItem x:cart)s+=x.amount();return s;}

    private void billActions(long id){page("🧾 Bill Saved: "+billNo(id));Button w=button("💬 WhatsApp Bill");box.addView(w);w.setOnClickListener(v->shareWhatsApp(id));Button p=button("🖨 Print / Save as PDF");box.addView(p);p.setOnClickListener(v->printBill(id));Button n=button("➕ New Bill");box.addView(n);n.setOnClickListener(v->bill());Button h=button("← Home");box.addView(h);h.setOnClickListener(v->home());box.addView(text(invoiceText(id),15));}

    private void medicines(){page("💊 Medicine Stock");EditText n=input("Medicine name");EditText price=input("Selling price ₹");price.setInputType(8194|2);EditText stock=input("Stock quantity");stock.setInputType(2);EditText exp=input("Expiry MM/YYYY");Button add=button("ADD MEDICINE");box.addView(add);add.setOnClickListener(v->{String name=n.getText().toString().trim();double p=parseDouble(price.getText().toString());int s=parseInt(stock.getText().toString());if(name.isEmpty()||p<0||s<0){toast("Check medicine details");return;}db.addMedicine(name,p,s,exp.getText().toString().trim());medicines();});Cursor c=db.medicines();while(c.moveToNext())box.addView(text("💊 "+c.getString(1)+"  ₹"+money(c.getDouble(2))+"\nStock: "+c.getInt(3)+"   Expiry: "+c.getString(4),16));c.close();addBack();}

    private void customers(){page("👤 Customers & Reminders");EditText n=input("Customer name");EditText p=input("Mobile number");p.setInputType(2);EditText note=input("Reminder note");Button add=button("ADD CUSTOMER");box.addView(add);add.setOnClickListener(v->{String name=n.getText().toString().trim();if(name.isEmpty()){toast("Enter customer name");return;}db.addCustomer(name,p.getText().toString().trim(),note.getText().toString().trim());customers();});Cursor c=db.customers();while(c.moveToNext()){String name=c.getString(1),phone=c.getString(2)==null?"":c.getString(2);box.addView(text("• "+name+"  "+phone+"\n  "+c.getString(3),16));Button r=button("🔔 Remind tomorrow — "+name);box.addView(r);r.setOnClickListener(v->scheduleReminder(name,phone));}c.close();addBack();}

    private void reports(){page("📊 Sales Reports");String day=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());double today=db.salesBetween(day+" 00:00",day+" 23:59");Calendar cal=Calendar.getInstance();cal.set(Calendar.DAY_OF_MONTH,1);String first=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(cal.getTime());double month=db.salesBetween(first+" 00:00",day+" 23:59");box.addView(text("Today Sales\n₹"+money(today)+"\n\nThis Month\n₹"+money(month),22));Button h=button("🧾 Sales History");box.addView(h);h.setOnClickListener(v->sales());addBack();}
    private void sales(){page("🧾 Sales History");Cursor c=db.bills();double sum=0;while(c.moveToNext()){long id=c.getLong(0);double total=c.getDouble(8);sum+=total;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.addView(text(c.getString(1)+"  •  "+c.getString(2)+"\n₹"+money(total)+"  •  "+c.getString(9),16));Button view=button("VIEW / SHARE / PDF");row.addView(view);view.setOnClickListener(v->billActions(id));box.addView(row);}c.close();box.addView(text("TOTAL SALES: ₹"+money(sum),21));addBack();}

    private void alerts(){page("⚠ Stock / Expiry Alerts");Cursor c=db.medicines();int low=0,near=0,expired=0;while(c.moveToNext()){int stock=c.getInt(3);String exp=c.getString(4);boolean ex=isExpired(exp),nr=isNearExpiry(exp);if(stock<=10){low++;box.addView(text("🔻 LOW STOCK: "+c.getString(1)+" — "+stock,16));}if(ex){expired++;box.addView(text("⛔ EXPIRED: "+c.getString(1)+" — "+exp,16));}else if(nr){near++;box.addView(text("⚠ NEAR EXPIRY: "+c.getString(1)+" — "+exp,16));}}c.close();if(low==0&&near==0&&expired==0)box.addView(text("✅ No current alerts",18));box.addView(text("Low stock: "+low+"\nNear expiry: "+near+"\nExpired: "+expired,18));addBack();}

    private void scheduleReminder(String customer,String phone){if(Build.VERSION.SDK_INT>=31){AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(!am.canScheduleExactAlarms()){try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));}catch(Exception ignored){}return;}}Intent i=new Intent(this,ReminderReceiver.class);i.putExtra("customer",customer);i.putExtra("message","Medicine refill reminder for "+phone);int request=(int)(System.currentTimeMillis()%100000000);PendingIntent pi=PendingIntent.getBroadcast(this,request,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,System.currentTimeMillis()+30L*24*60*60*1000,pi);toast("Refill reminder scheduled");}

    private String invoiceText(long id){StringBuilder s=new StringBuilder();Cursor b=db.getBill(id);if(!b.moveToFirst()){b.close();return "";}s.append("Sri Krishna Medicals\nPennagaram\n").append("Bill No: ").append(b.getString(1)).append("\nCustomer: ").append(b.getString(2)).append("\nPhone: ").append(b.getString(3)).append("\nDate: ").append(b.getString(9)).append("\n\n");s.append("Items:\n");Cursor it=db.getBillItems(id);while(it.moveToNext())s.append(it.getString(3)).append(" × ").append(it.getInt(5)).append(" = ₹").append(money(it.getDouble(6))).append("\n");it.close();s.append("\nSubtotal: ₹").append(money(b.getDouble(4))).append("\nDiscount: ₹").append(money(b.getDouble(5))).append("\nGST: ₹").append(money(b.getDouble(7))).append("\nGrand Total: ₹").append(money(b.getDouble(8)));b.close();return s.toString();}

    private void shareWhatsApp(long id){String t=invoiceText(id);Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,t);i.setPackage("com.whatsapp");try{startActivity(i);}catch(Exception e){i.setPackage(null);startActivity(Intent.createChooser(i,"Share bill"));}}
    private void printBill(long id){final WebView w=new WebView(this);w.loadDataWithBaseURL(null,"<html><body style='font-family:sans-serif'>"+invoiceText(id).replace("\n","<br>")+"</body></html>","text/html","UTF-8",null);w.setWebViewClient(new android.webkit.WebViewClient(){@Override public void onPageFinished(WebView view,String url){PrintManager pm=(PrintManager)getSystemService(PRINT_SERVICE);pm.print("SKMedKART-"+billNo(id),view.createPrintDocumentAdapter("SKMedKART"),new PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).setMinMargins(PrintAttributes.Margins.NO_MARGINS).build());}});}

    private String billNo(long id){Cursor c=db.getBill(id);String s="SKM-"+String.format(Locale.getDefault(),"%06d",id);if(c.moveToFirst()&&c.getString(1)!=null)s=c.getString(1);c.close();return s;}
    private String now(){return new SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.getDefault()).format(new Date());}
    private String money(double v){return String.format(Locale.getDefault(),"%.2f",v);}
    private int parseInt(String s){try{return Integer.parseInt(s.trim());}catch(Exception e){return 0;}}
    private double parseDouble(String s){try{return Double.parseDouble(s.trim());}catch(Exception e){return 0;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void requestNotificationPermission(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},10);}
    private boolean isExpired(String e){Date d=parseExpiry(e);if(d==null)return false;Calendar c=Calendar.getInstance();c.set(Calendar.DAY_OF_MONTH,1);c.set(Calendar.HOUR_OF_DAY,0);c.set(Calendar.MINUTE,0);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);return d.before(c.getTime());}
    private boolean isNearExpiry(String e){Date d=parseExpiry(e);if(d==null)return false;Calendar now=Calendar.getInstance();Calendar lim=Calendar.getInstance();lim.add(Calendar.MONTH,2);return !d.before(now.getTime())&&d.before(lim.getTime());}
    private Date parseExpiry(String e){if(e==null||e.trim().isEmpty())return null;try{return new SimpleDateFormat("MM/yyyy",Locale.getDefault()).parse(e.trim());}catch(ParseException x){return null;}}
}
