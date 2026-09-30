package com.midinero.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import android.content.Context

class MainActivity : Activity() {
    private lateinit var root: LinearLayout
    private var primary = Color.rgb(36,137,93)
    private val prefs by lazy { getSharedPreferences("mi_dinero", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowInsets.apply(window)
        build()
    }

    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    private fun text(s:String,size:Float=15f,bold:Boolean=false): TextView {
        val t=TextView(this); t.text=s; t.textSize=size; t.setTextColor(Color.rgb(35,42,39))
        if(bold) t.setTypeface(null,1); t.setPadding(dp(4),dp(4),dp(4),dp(4)); return t
    }
    private fun button(label:String,onClick:()->Unit): Button {
        val b=Button(this); b.text=label; b.setOnClickListener{onClick()}; b.setTextColor(primary); return b
    }
    private fun card(): LinearLayout {
        val c=LinearLayout(this); c.orientation=LinearLayout.VERTICAL; c.setPadding(dp(16),dp(14),dp(16),dp(14))
        c.setBackgroundColor(Color.WHITE); val p=LinearLayout.LayoutParams(-1,-2); p.setMargins(0,dp(7),0,dp(7)); c.layoutParams=p; return c
    }
    private fun build(){
        root=LinearLayout(this); root.orientation=LinearLayout.VERTICAL; root.setBackgroundColor(Color.rgb(246,250,248))
        val content=FrameLayout(this); content.layoutParams=LinearLayout.LayoutParams(-1,0,1f)
        root.addView(content)
        val nav=LinearLayout(this); nav.gravity=Gravity.CENTER; nav.setBackgroundColor(Color.WHITE)
        val navp=LinearLayout.LayoutParams(-1,dp(68)); nav.layoutParams=navp
        val items=listOf("⌂\nInicio","↕\nMovimientos","◔\nPresupuestos","◉\nMi perfil")
        items.forEachIndexed{ i,label -> val b=button(label){ showPage(content,i) }; b.layoutParams=LinearLayout.LayoutParams(0,-1,1f); nav.addView(b) }
        root.addView(nav); setContentView(root); showPage(content,0)
    }
    private fun showPage(frame:FrameLayout,page:Int){
        frame.removeAllViews()
        val scroll=ScrollView(this); val box=LinearLayout(this); box.orientation=LinearLayout.VERTICAL; box.setPadding(dp(16),dp(14),dp(16),dp(18))
        when(page){
            0->home(box,frame)
            1->movements(box)
            2->budgets(box)
            else->profile(box,frame)
        }
        scroll.addView(box); frame.addView(scroll)
    }
    private fun header(box:LinearLayout,title:String,subtitle:String=""){
        box.addView(text(title,28f,true)); if(subtitle.isNotEmpty()) box.addView(text(subtitle,13f))
    }
    private fun home(box:LinearLayout,frame:FrameLayout){
        header(box,"Mi Dinero","Tu control financiero")
        val c=card(); c.addView(text("Saldo total",14f)); c.addView(text("$0.00",30f,true)); c.addView(text("Equivalente convertido a USD",12f)); box.addView(c)
        val b=card(); b.addView(text("Mis saldos",17f,true)); b.addView(text("Bs 0.00",16f)); b.addView(text("$0.00",16f)); b.addView(button("Editar saldos"){ Toast.makeText(this,"Saldos",Toast.LENGTH_SHORT).show() }); box.addView(b)
        val r=card(); r.addView(text("Tasa BCV",17f,true)); r.addView(text("Consultar tasa oficial",14f)); r.addView(button("Ver tasas BCV"){Toast.makeText(this,"Historial BCV",Toast.LENGTH_SHORT).show()}); box.addView(r)
        val cv=card(); cv.addView(text("Conversor",17f,true)); val input=EditText(this); input.hint="Monto"; input.inputType=2; cv.addView(input); cv.addView(button("Bs → USD"){Toast.makeText(this,"Conversión preparada",Toast.LENGTH_SHORT).show()}); box.addView(cv)
        val m=card(); m.addView(text("Últimos movimientos",17f,true)); m.addView(text("Todavía no hay movimientos.",13f)); m.addView(button("＋ Agregar movimiento"){showPage(frame,1)}); box.addView(m)
    }
    private fun movements(box:LinearLayout){ header(box,"Movimientos","Ingresos y gastos"); val c=card(); c.addView(text("No hay movimientos registrados.",15f)); c.addView(button("＋ Nuevo movimiento"){Toast.makeText(this,"Nuevo movimiento",Toast.LENGTH_SHORT).show()}); box.addView(c) }
    private fun budgets(box:LinearLayout){ header(box,"Presupuestos","Controla tus límites mensuales"); val c=card(); c.addView(text("Sin presupuestos creados.",15f)); c.addView(button("＋ Agregar presupuesto"){Toast.makeText(this,"Presupuesto",Toast.LENGTH_SHORT).show()}); box.addView(c) }
    private fun profile(box:LinearLayout,frame:FrameLayout){
        header(box,"Mi perfil","Personaliza tu aplicación")
        val c=card(); c.addView(text("Perfil",17f,true)); val name=EditText(this); name.hint="Nombre de usuario"; name.setText(prefs.getString("name","Mi usuario")); c.addView(name); c.addView(button("Guardar"){prefs.edit().putString("name",name.text.toString()).apply();Toast.makeText(this,"Perfil guardado",Toast.LENGTH_SHORT).show()}); box.addView(c)
        val theme=card(); theme.addView(text("Apariencia",17f,true)); theme.addView(text("Color principal",14f))
        listOf("Verde" to Color.rgb(36,137,93),"Morado" to Color.rgb(109,74,255),"Bosque" to Color.rgb(29,111,90),"Azul" to Color.rgb(45,105,190),"Negro" to Color.rgb(35,35,35)).forEach{ (n,cx)-> val b=button(n){primary=cx; prefs.edit().putInt("color",cx).apply(); build()}; theme.addView(b) }
        box.addView(theme)
    }
    object WindowInsets { fun apply(w:Window){ w.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR } }
}
