package com.midinero.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val prefs by lazy { getSharedPreferences("mi_dinero", Context.MODE_PRIVATE) }
    private val handler = Handler(Looper.getMainLooper())
    private var primary = Color.rgb(109,74,255)
    private var dark = false
    private var vesBalance = 0.0
    private var rate = 857.89
    private var rateDate = "29/09/2026"
    private val rateHistory = linkedMapOf<String,Double>()
    private val moves = mutableListOf<Move>()
    data class Move(val id:Long,val type:String,val name:String,val amount:Double,val currency:String,val date:String)

    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        loadState(); applySystemBars(); buildShell(); showPage(0); refreshRate(false)
        handler.postDelayed(object:Runnable{override fun run(){refreshRate(false);handler.postDelayed(this,300000L)}},300000L)
    }
    override fun onBackPressed(){if(content.childCount>0&&content.tag!=0)showPage(0)else super.onBackPressed()}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun loadState(){
        primary=prefs.getInt("color",primary);dark=prefs.getBoolean("dark",false);vesBalance=prefs.getString("ves","0")?.toDoubleOrNull()?:0.0
        rate=prefs.getString("rate","857.89")?.toDoubleOrNull()?:857.89;rateDate=prefs.getString("rateDate","29/09/2026")?:"29/09/2026"
        (prefs.getString("history","")?:"").split("|").forEach{p->val x=p.split(":");if(x.size==2)x[1].toDoubleOrNull()?.let{rateHistory[x[0]]=it}}
        if(!rateHistory.containsKey(rateDate))rateHistory[rateDate]=rate
        (prefs.getString("moves","")?:"").split("||").filter{it.isNotBlank()}.forEach{r->val p=r.split("|");if(p.size>=6)moves.add(Move(p[0].toLongOrNull()?:System.currentTimeMillis(),p[1],p[2],p[3].toDoubleOrNull()?:0.0,p[4],p[5]))}
    }
    private fun saveState(){
        prefs.edit().putInt("color",primary).putBoolean("dark",dark).putString("ves",vesBalance.toString()).putString("rate",rate.toString()).putString("rateDate",rateDate)
            .putString("history",rateHistory.entries.joinToString("|"){it.key+":"+it.value}).putString("moves",moves.joinToString("||"){it.id.toString()+"|"+it.type+"|"+it.name+"|"+it.amount+"|"+it.currency+"|"+it.date}).apply()
    }
    private fun applySystemBars(){
        window.statusBarColor=bg();window.navigationBarColor=bg()
        if(android.os.Build.VERSION.SDK_INT>=30){window.setDecorFitsSystemWindows(false);window.insetsController?.setSystemBarsAppearance(if(!dark)WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS else 0,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)}
        else window.decorView.systemUiVisibility=if(!dark)View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0
    }
    private fun buildShell(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(bg())}
        root.setOnApplyWindowInsetsListener{v,i->if(android.os.Build.VERSION.SDK_INT>=30){val s=i.getInsets(WindowInsets.Type.statusBars());val n=i.getInsets(WindowInsets.Type.navigationBars());v.setPadding(0,s.top,0,n.bottom)};i}
        content=FrameLayout(this).apply{layoutParams=LinearLayout.LayoutParams(-1,0,1f)}
        nav=LinearLayout(this).apply{gravity=Gravity.CENTER;setBackgroundColor(panel());setPadding(dp(4),dp(4),dp(4),dp(4));layoutParams=LinearLayout.LayoutParams(-1,dp(68))}
        root.addView(content);root.addView(nav);setContentView(root)
    }
    private fun showPage(page:Int){
        content.removeAllViews();content.tag=page
        val scroll=ScrollView(this).apply{isFillViewport=true;setBackgroundColor(bg())}
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(18))}
        scroll.addView(box);content.addView(scroll)
        when(page){0->home(box);1->movements(box);2->profile(box);3->settings(box)};renderNav(page)
    }
    private fun renderNav(selected:Int){
        nav.removeAllViews();listOf("⌂\nInicio","↕\nMovimientos","◉\nMi perfil","⚙\nConfiguraciones").forEachIndexed{i,label->
            val b=Button(this).apply{text=label;textSize=10f;isAllCaps=false;setTextColor(if(i==selected)primary else muted());setBackgroundColor(Color.TRANSPARENT);setPadding(0,0,0,0);setOnClickListener{showPage(i)}}
            b.layoutParams=LinearLayout.LayoutParams(0,-1,1f);nav.addView(b)
        }
    }
    private fun header(box:LinearLayout,title:String,subtitle:String="",back:Boolean=false){
        val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
        if(back){val b=Button(this).apply{text="‹";textSize=28f;setTextColor(primary);setBackgroundColor(Color.TRANSPARENT);setPadding(0,0,dp(8),0);setOnClickListener{showPage(0)}};row.addView(b,LinearLayout.LayoutParams(dp(44),dp(52)))}
        val col=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};col.addView(text(title,27f,true));if(subtitle.isNotEmpty())col.addView(text(subtitle,13f));row.addView(col,LinearLayout.LayoutParams(0,-2,1f));box.addView(row);space(box,4)
    }
    private fun home(box:LinearLayout){
        header(box,"Mi Dinero","Tu control financiero")
        val total=if(rate>0)vesBalance/rate else 0.0
        val c=card();c.addView(text("Saldo total",14f));c.addView(text(moneyUsd(total),30f,true));c.addView(text("Equivalente convertido a USD",12f));box.addView(c)
        val b=card();b.addView(text("Mis saldos",18f,true));b.addView(text(moneyBs(vesBalance),17f));b.addView(text("≈ ${moneyUsd(total)}",17f));b.addView(button("EDITAR SALDO EN BS"){editBalance()});box.addView(b)
        val r=card();r.addView(text("Tasa BCV",18f,true));r.addView(text("1 USD = ${fmtRate(rate)} Bs · $rateDate",14f));r.addView(button("VER TASAS BCV"){showRateHistory()});box.addView(r)
        val cv=card();cv.addView(text("Conversor",18f,true));val input=EditText(this).apply{hint="Monto";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setTextColor(textColor());setHintTextColor(muted())};val spinner=Spinner(this);spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Bs → USD","USD → Bs"));val result=text("Resultado: —",16f,true);cv.addView(input);cv.addView(spinner);cv.addView(button("CONVERTIR"){val n=input.text.toString().replace(",",".").toDoubleOrNull()?:0.0;result.text=if(spinner.selectedItemPosition==0)"Resultado: ${moneyUsd(if(rate>0)n/rate else 0.0)}" else "Resultado: ${moneyBs(n*rate)}"});cv.addView(result);box.addView(cv)
        val m=card();m.addView(text("Últimos movimientos",18f,true));if(moves.isEmpty())m.addView(text("Todavía no hay movimientos.",13f))else moves.takeLast(5).asReversed().forEach{addMoveRow(m,it,true)};m.addView(button("＋ AGREGAR MOVIMIENTO"){showPage(1);addMovementDialog()});box.addView(m)
    }
    private fun movements(box:LinearLayout){
        header(box,"Movimientos","Ingresos y gastos")
        val s=card();s.addView(text("Resumen del mes",18f,true));val inc=moves.filter{it.type=="income"}.sumOf{toBs(it)};val exp=moves.filter{it.type=="expense"}.sumOf{toBs(it)};s.addView(text("Ingresos: ${moneyBs(inc)}",14f));s.addView(text("Gastos: ${moneyBs(exp)}",14f));s.addView(text("Balance del mes: ${moneyBs(inc-exp)}",14f,true));box.addView(s);box.addView(button("＋ AGREGAR MOVIMIENTO"){addMovementDialog()});if(moves.isEmpty())box.addView(text("No hay movimientos registrados.",14f));moves.asReversed().forEach{addMoveRow(box,it,false)}
    }
    private fun addMoveRow(parent:LinearLayout,move:Move,compact:Boolean){
        val c=card();val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};info.addView(text(move.name,if(compact)14f else 15f,true));info.addView(text("${if(move.type=="income")"Ingreso" else "Gasto"} · ${move.currency} · ${move.date}",11f));row.addView(info,LinearLayout.LayoutParams(0,-2,1f));row.addView(text(if(move.type=="income")"+${fmtAmount(move.amount,move.currency)}" else "-${fmtAmount(move.amount,move.currency)}",14f,true));val del=button("✕"){deleteMove(move.id)};del.layoutParams=LinearLayout.LayoutParams(dp(48),dp(48));row.addView(del);c.addView(row);parent.addView(c)
    }
    private fun profile(box:LinearLayout){
        header(box,"Mi perfil","Personaliza tu usuario");val c=card();val avatar=ImageView(this).apply{scaleType=ImageView.ScaleType.CENTER_CROP;setBackground(circle(primary));prefs.getString("photo",null)?.let{try{setImageURI(Uri.parse(it))}catch(_:Exception){}}};val ap=LinearLayout.LayoutParams(dp(92),dp(92));ap.gravity=Gravity.CENTER;c.addView(avatar,ap);c.addView(button("CAMBIAR FOTO"){pickPhoto()});val name=EditText(this).apply{hint="Nombre de usuario";setText(prefs.getString("name","Mi usuario"));setTextColor(textColor());setHintTextColor(muted())};c.addView(name);c.addView(button("GUARDAR PERFIL"){prefs.edit().putString("name",name.text.toString().trim().ifBlank{"Mi usuario"}).apply();toast("Perfil guardado")});box.addView(c)
    }
    private fun settings(box:LinearLayout){
        header(box,"Configuraciones","Ajusta Mi Dinero a tu dispositivo",true)
        val theme=card();theme.addView(text("Apariencia",18f,true));val sw=Switch(this).apply{isChecked=dark;text="Activar modo oscuro";setTextColor(textColor());setOnCheckedChangeListener{_,v->dark=v;prefs.edit().putBoolean("dark",dark).apply();applySystemBars();buildShell();showPage(3)}};theme.addView(sw);box.addView(theme)
        val colors=card();colors.addView(text("Color principal",18f,true));colors.addView(text("La interfaz completa usa el color seleccionado.",13f));listOf("Verde" to Color.rgb(36,137,93),"Morado" to Color.rgb(109,74,255),"Azul" to Color.rgb(45,105,190),"Naranja" to Color.rgb(230,130,35),"Rosa" to Color.rgb(210,70,130)).forEach{(n,c)->colors.addView(button(n){primary=c;prefs.edit().putInt("color",c).apply();buildShell();showPage(3)})};box.addView(colors)
        val bcv=card();bcv.addView(text("Tasa BCV",18f,true));bcv.addView(text("Actualización automática cuando haya una nueva tasa disponible.",13f));bcv.addView(button("VER HISTORIAL BCV"){showRateHistory()});bcv.addView(button("ACTUALIZAR AHORA"){refreshRate(true)});box.addView(bcv)
        val data=card();data.addView(text("Datos",18f,true));data.addView(button("RESTABLECER SALDO Y MOVIMIENTOS"){confirmReset()});box.addView(data)
    }
    private fun editBalance(){
        val input=EditText(this).apply{inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL;setText(if(vesBalance==0.0)""else vesBalance.toString());selectAll()}
        AlertDialog.Builder(this).setTitle("Mi saldo en Bs").setMessage("Introduce solo tu saldo en bolívares. El equivalente en USD se calcula automáticamente con la tasa BCV.").setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Guardar"){_,_->vesBalance=input.text.toString().replace(",",".").toDoubleOrNull()?:0.0;saveState();showPage(0)}.show()
    }
    private fun addMovementDialog(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0)};val name=EditText(this).apply{hint="Nombre (ej. comida, internet, salario)"};val amount=EditText(this).apply{hint="Monto";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};val type=Spinner(this);type.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Ingreso","Gasto"));val cur=Spinner(this);cur.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,listOf("Bs","USD"));box.addView(name);box.addView(amount);box.addView(type);box.addView(cur)
        AlertDialog.Builder(this).setTitle("Nuevo movimiento").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar"){_,_->val n=name.text.toString().trim();val a=amount.text.toString().replace(",",".").toDoubleOrNull()?:0.0;if(n.isBlank()||a<=0){toast("Completa nombre y monto");return@setPositiveButton};val t=if(type.selectedItemPosition==0)"income"else"expense";val c=if(cur.selectedItemPosition==0)"Bs"else"USD";val d=SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(Date());val m=Move(System.currentTimeMillis(),t,n,a,c,d);if(t=="income")vesBalance+=toBs(m)else vesBalance-=toBs(m);moves.add(m);saveState();showPage(1)}.show()
    }
    private fun deleteMove(id:Long){val i=moves.indexOfFirst{it.id==id};if(i<0)return;val m=moves[i];if(m.type=="income")vesBalance-=toBs(m)else vesBalance+=toBs(m);moves.removeAt(i);saveState();showPage(1)}
    private fun toBs(m:Move)=if(m.currency=="USD")m.amount*rate else m.amount
    private fun showRateHistory(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(8),0,dp(8),0)};box.addView(text("Actual: 1 USD = ${fmtRate(rate)} Bs\nFecha: $rateDate",16f,true));rateHistory.entries.toList().asReversed().forEach{(d,v)->val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};row.addView(text("$d · ${fmtRate(v)} Bs",14f),LinearLayout.LayoutParams(0,dp(48),1f));row.addView(button("USAR"){rate=v;rateDate=d;saveState();showPage(0)});box.addView(row)};box.addView(button("＋ AGREGAR / EDITAR DÍA"){editHistoricalRate()});AlertDialog.Builder(this).setTitle("Tasas BCV").setView(box).setNegativeButton("Cerrar",null).show()
    }
    private fun editHistoricalRate(){val d=EditText(this).apply{hint="Fecha (dd/MM/yyyy)"};val v=EditText(this).apply{hint="Tasa Bs por USD";inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL};val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),0);addView(d);addView(v)};AlertDialog.Builder(this).setTitle("Tasa anterior").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar"){_,_->val ds=d.text.toString().trim();val rv=v.text.toString().replace(",",".").toDoubleOrNull();if(ds.isNotBlank()&&rv!=null&&rv>0){rateHistory[ds]=rv;saveState();showRateHistory()}}.show()}
    private fun refreshRate(manual:Boolean){
        thread{try{val c=URL("https://bcv.today/api/v1/rate.json").openConnection() as HttpURLConnection;c.connectTimeout=7000;c.readTimeout=7000;c.requestMethod="GET";val body=c.inputStream.bufferedReader().use{it.readText()};c.disconnect();val j=JSONObject(body);var found:Double?=null;for(k in listOf("rate","usd","usd_ves","value","valor")){if(j.has(k)){val x=j.optDouble(k,Double.NaN);if(!x.isNaN()&&x>0){found=x;break}}};if(found==null){found=Regex("\\\"(?:rate|usd|value|valor)\\\"\\s*:\\s*([0-9.]+)").find(body)?.groupValues?.getOrNull(1)?.toDoubleOrNull()};if(found!=null&&found!!>0)handler.post{rate=found!!;rateDate=SimpleDateFormat("dd/MM/yyyy",Locale.getDefault()).format(Date());rateHistory[rateDate]=rate;saveState();if(manual){toast("Tasa BCV actualizada");showPage(0)}}else if(manual)handler.post{toast("No se pudo leer la tasa BCV")}}catch(_:Exception){if(manual)handler.post{toast("Sin conexión. Se conserva la última tasa guardada.")}}}
    }
    private fun pickPhoto(){startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply{type="image/*";addCategory(Intent.CATEGORY_OPENABLE)},42)}
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data);if(requestCode==42&&resultCode==RESULT_OK)data?.data?.let{u->try{contentResolver.takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);prefs.edit().putString("photo",u.toString()).apply();showPage(2)}catch(_:Exception){}}}
    private fun confirmReset(){AlertDialog.Builder(this).setTitle("Restablecer datos").setMessage("Se borrarán saldo y movimientos. ¿Continuar?").setNegativeButton("Cancelar",null).setPositiveButton("Restablecer"){_,_->vesBalance=0.0;moves.clear();saveState();showPage(0)}.show()}
    private fun card():LinearLayout{val c=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(14),dp(16),dp(14));setBackgroundColor(panel())};val p=LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(7),0,dp(7));c.layoutParams=p;return c}
    private fun text(s:String,size:Float=15f,bold:Boolean=false):TextView{val t=TextView(this);t.text=s;t.textSize=size;t.setTextColor(textColor());if(bold)t.setTypeface(null,1);t.setPadding(dp(2),dp(4),dp(2),dp(4));return t}
    private fun button(label:String,onClick:()->Unit):Button{val b=Button(this);b.text=label;b.textSize=12f;b.isAllCaps=false;b.setTextColor(primary);b.setOnClickListener{onClick()};return b}
    private fun space(box:LinearLayout,h:Int){val v=Space(this);v.layoutParams=LinearLayout.LayoutParams(1,dp(h));box.addView(v)}
    private fun bg()=if(dark)Color.rgb(18,20,24)else Color.rgb(246,248,251)
    private fun panel()=if(dark)Color.rgb(30,33,39)else Color.WHITE
    private fun textColor()=if(dark)Color.rgb(240,243,247)else Color.rgb(31,37,43)
    private fun muted()=if(dark)Color.rgb(170,178,188)else Color.rgb(105,113,123)
    private fun circle(c:Int)=GradientDrawable().apply{shape=GradientDrawable.OVAL;setColor(c)}
    private fun moneyBs(v:Double)="Bs "+String.format(Locale.US,"%,.2f",v).replace(',','X').replace('.',',').replace('X','.')
    private fun moneyUsd(v:Double)="$"+String.format(Locale.US,"%,.2f",v)
    private fun fmtRate(v:Double)=String.format(Locale.US,"%,.2f",v).replace(',','X').replace('.',',').replace('X','.')
    private fun fmtAmount(v:Double,c:String)=if(c=="USD")moneyUsd(v)else moneyBs(v)
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
