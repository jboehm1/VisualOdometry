package dev.jboehm.vectorvio;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.Locale;

/**
 * Full-screen, readable settings, without nested AlertDialogs or index-based routing.
 * Live engine actions are explicitly delegated to MainActivity. Pure preference
 * toggles stay on this page and are applied when the camera activity resumes.
 */
public final class SettingsActivity extends Activity {
    public static final String EXTRA_ACTION="dev.jboehm.vectorvio.settings.ACTION";
    private static final int LOCATION_PERMISSION=211;
    private static final int BG=0xFF071722, CARD=0xFF142C3B, BORDER=0xFF315163;
    private static final int WHITE=0xFFF0F8FC, MUTED=0xFFB4C8D3, CYAN=0xFF72ECD8;
    private SharedPreferences prefs;
    private LinearLayout tabs, content;
    private HorizontalScrollView tabStrip;
    private ScrollView listScroll;
    private EditText search;
    private SettingsCatalog.Section selected=SettingsCatalog.Section.NAVIGATION;
    private String filter="";
    private int densityDp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable background(int color,int radius,int stroke){
        GradientDrawable b=new GradientDrawable();b.setColor(color);b.setCornerRadius(densityDp(radius));
        if(stroke!=0)b.setStroke(densityDp(1),stroke);return b;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(color);t.setTextSize(size);
        t.setGravity(Gravity.CENTER_VERTICAL);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        return t;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("spatial_explorer",MODE_PRIVATE);
        if(state!=null){
            String section=state.getString("section");
            if(section!=null)try{selected=SettingsCatalog.Section.valueOf(section);}catch(IllegalArgumentException ignored){}
        }
        LinearLayout root=vertical();root.setBackgroundColor(BG);
        root.setPadding(densityDp(16),densityDp(12),densityDp(16),densityDp(10));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",38,WHITE,true);back.setGravity(Gravity.CENTER);
        back.setContentDescription("Retour à la caméra");back.setTag("settings_back");
        header.addView(back,new LinearLayout.LayoutParams(densityDp(48),densityDp(54)));
        back.setOnClickListener(v->finish());
        LinearLayout heading=vertical();
        TextView title=text("PARAMÈTRES",23,WHITE,true);heading.addView(title);
        String version=AppIdentity.version(this);
        TextView subtitle=text("VECTOR VIO  •  v"+version,12,CYAN,true);
        subtitle.setTag("settings_version");heading.addView(subtitle);
        TextView build=text(AppIdentity.buildLabel(this),10,MUTED,false);
        build.setTag("settings_build");heading.addView(build);
        header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(header,new LinearLayout.LayoutParams(-1,densityDp(64)));
        TextView helper=text("Toutes les options, expliquées et regroupées.",13,MUTED,false);
        helper.setPadding(densityDp(6),densityDp(4),0,densityDp(12));root.addView(helper);

        search=new EditText(this);
        search.setSingleLine(true);search.setTextSize(16);search.setTextColor(WHITE);
        search.setHintTextColor(MUTED);search.setHint("Rechercher un réglage…");
        search.setBackground(background(CARD,12,BORDER));
        search.setPadding(densityDp(16),0,densityDp(14),0);
        search.setTag("settings_search");
        root.addView(search,new LinearLayout.LayoutParams(-1,densityDp(54)));
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                filter=s.toString().trim().toLowerCase(Locale.ROOT);renderRows();
            }
            @Override public void afterTextChanged(Editable e){}
        });

        tabStrip=new HorizontalScrollView(this);tabStrip.setHorizontalScrollBarEnabled(false);
        tabs=new LinearLayout(this);tabs.setGravity(Gravity.CENTER_VERTICAL);tabs.setPadding(0,densityDp(11),0,densityDp(11));
        tabStrip.addView(tabs,new HorizontalScrollView.LayoutParams(-2,-1));
        root.addView(tabStrip,new LinearLayout.LayoutParams(-1,densityDp(70)));
        listScroll=new ScrollView(this);listScroll.setFillViewport(true);listScroll.setClipToPadding(false);
        content=vertical();content.setPadding(densityDp(2),0,densityDp(2),densityDp(20));
        listScroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        root.addView(listScroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
        drawTabs();renderRows();
    }
    @Override public void onSaveInstanceState(Bundle state){
        state.putString("section",selected.name());super.onSaveInstanceState(state);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=LOCATION_PERMISSION)return;
        boolean fine=checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        prefs.edit().putBoolean("gps_speed_enabled",fine||coarse).apply();
        renderRows();
    }
    private void drawTabs(){
        if(tabs==null)return;
        tabs.removeAllViews();
        for(SettingsCatalog.Section group:SettingsCatalog.Section.values()){
            final boolean active=group==selected && filter.isEmpty();
            TextView chip=text(SettingsPresentation.tab(group),15,active?BG:WHITE,true);
            chip.setGravity(Gravity.CENTER);chip.setMinHeight(densityDp(46));
            chip.setPadding(densityDp(18),0,densityDp(18),0);
            chip.setBackground(background(active?CYAN:CARD,13,active?CYAN:BORDER));
            chip.setTag("tab_"+group.name());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,densityDp(46));
            p.setMargins(0,0,densityDp(9),0);tabs.addView(chip,p);
            chip.setOnClickListener(v->{
                if(!filter.isEmpty()){search.setText("");search.clearFocus();}
                selected=group;drawTabs();renderRows();listScroll.smoothScrollTo(0,0);
            });
        }
    }
    private void addHeading(String title,String caption){
        TextView label=text(title,22,WHITE,true);label.setPadding(densityDp(4),densityDp(9),0,densityDp(2));
        content.addView(label,new LinearLayout.LayoutParams(-1,-2));
        TextView detail=text(caption,14,MUTED,false);detail.setPadding(densityDp(4),0,densityDp(5),densityDp(13));
        detail.setLineSpacing(densityDp(2),1.0f);content.addView(detail);
    }
    private void renderRows(){
        if(content==null)return;
        content.removeAllViews();drawTabs();
        if(!filter.isEmpty()){
            addHeading("Résultats", "Recherche dans toutes les catégories");
            int found=0;
            for(SettingsCatalog.Section section:SettingsCatalog.Section.values()){
                for(SettingsCatalog.Item item:SettingsCatalog.items(section)){
                    String hay=(SettingsPresentation.title(item.action)+" "+
                        SettingsPresentation.explanation(item.action)+" "+SettingsPresentation.tab(section)).toLowerCase(Locale.ROOT);
                    if(hay.contains(filter)){addItem(item);found++;}
                }
            }
            if(found==0){
                TextView empty=text("Aucun réglage trouvé. Essayez « GPS », « caméra », « GPX »…",16,MUTED,false);
                empty.setPadding(densityDp(12),densityDp(30),densityDp(12),densityDp(30));content.addView(empty);
            }
        }else{
            addHeading(SettingsPresentation.tab(selected),selected.subtitle);
            for(SettingsCatalog.Item item:SettingsCatalog.items(selected))addItem(item);
            TextView note=text(SettingsPresentation.note(selected),14,MUTED,false);
            note.setLineSpacing(densityDp(3),1f);note.setPadding(densityDp(15),densityDp(18),densityDp(15),densityDp(20));
            content.addView(note);
        }
    }
    private void addItem(SettingsCatalog.Item item){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(background(CARD,14,BORDER));
        card.setPadding(densityDp(16),densityDp(14),densityDp(12),densityDp(14));
        card.setMinimumHeight(densityDp(94));card.setTag("setting_"+item.action.name());
        LinearLayout words=vertical();
        TextView title=text(SettingsPresentation.title(item.action),17,WHITE,true);
        words.addView(title);
        TextView description=text(SettingsPresentation.explanation(item.action),14,MUTED,false);
        description.setLineSpacing(densityDp(3),1f);description.setPadding(0,densityDp(5),densityDp(6),0);
        words.addView(description);
        card.addView(words,new LinearLayout.LayoutParams(0,-2,1f));
        if(SettingsPresentation.toggle(item.action)){
            boolean active=isChecked(item.action);
            Switch toggle=new Switch(this);toggle.setChecked(active);toggle.setShowText(false);
            toggle.setMinWidth(densityDp(58));toggle.setMinHeight(densityDp(50));
            toggle.setContentDescription(SettingsPresentation.title(item.action)+" : "+(active?"activé":"désactivé"));
            toggle.setTag("switch_"+item.action.name());
            card.addView(toggle,new LinearLayout.LayoutParams(densityDp(62),densityDp(58)));
            toggle.setOnCheckedChangeListener((button,checked)->changeToggle(item.action,checked));
            card.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));
        }else{
            TextView open=text("›",29,CYAN,true);open.setGravity(Gravity.CENTER);
            card.addView(open,new LinearLayout.LayoutParams(densityDp(35),densityDp(55)));
            card.setOnClickListener(v->returnAction(item.action));
        }
        LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);
        margin.setMargins(0,0,0,densityDp(10));content.addView(card,margin);
    }
    private boolean isChecked(SettingsCatalog.Action a){
        switch(a){
            case GPX_EXTENDED_PREVIEW:return prefs.getBoolean("gpx_extended_preview",true);
            case GPS_TOGGLE:return prefs.getBoolean("gps_speed_enabled",true) &&
                (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED);
            case IMU_TOGGLE:return prefs.getBoolean("imu_overlay_enabled",false);
            case UNKNOWN_GROUND:return prefs.getBoolean("estimated_ground",false);
            case BIKE_VIEW_HINTS:return prefs.getBoolean("bike_view_hints",true);
            default:throw new IllegalArgumentException("Not toggle "+a);
        }
    }
    private void changeToggle(SettingsCatalog.Action a,boolean checked){
        String key;
        switch(a){
            case GPX_EXTENDED_PREVIEW:key="gpx_extended_preview";break;
            case GPS_TOGGLE:key="gps_speed_enabled";break;
            case IMU_TOGGLE:key="imu_overlay_enabled";break;
            case UNKNOWN_GROUND:key="estimated_ground";break;
            case BIKE_VIEW_HINTS:key="bike_view_hints";break;
            default:throw new IllegalArgumentException("Not toggle "+a);
        }
        if(a==SettingsCatalog.Action.GPS_TOGGLE && checked &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            prefs.edit().putBoolean(key,false).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION_PERMISSION);
            return;
        }
        prefs.edit().putBoolean(key,checked).apply();
    }
    private void returnAction(SettingsCatalog.Action a){
        setResult(RESULT_OK,new Intent().putExtra(EXTRA_ACTION,a.name()));
        finish();
    }
}
