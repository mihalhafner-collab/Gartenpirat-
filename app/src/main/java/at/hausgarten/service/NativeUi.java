package at.hausgarten.service;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;

/** Shared presentation only. Colors mirror assets/tokens.css; no connection state is stored here. */
final class NativeUi {
    static final int BG=0xfff5f7f5, SURFACE=0xffffffff, TEXT=0xff17241d, MUTED=0xff53655a, LINE=0xffdce4de;
    static final int GREEN=0xff164d38, GREEN_BTN=0xff23744e, SAGE=0xffedf3ee;
    static final int INFO_BG=0xffe8f1fb, INFO_TEXT=0xff1f5c8f, WARN_BG=0xfffff0cd, WARN_TEXT=0xff765010;
    static final int DANGER=0xff8a2e25, DANGER_BG=0xfffbeceb;
    private NativeUi(){}
    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static GradientDrawable shape(Context context,int fill,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(context,radius));if(stroke!=0)d.setStroke(dp(context,1),stroke);return d;}
    static Button style(Button button,int kind){Context c=button.getContext();button.setAllCaps(false);button.setTextSize(16);button.setTypeface(null,Typeface.BOLD);button.setMinHeight(dp(c,52));button.setMinimumHeight(dp(c,52));button.setStateListAnimator(null);button.setElevation(0);button.setBackgroundTintList(null);button.setBackground(shape(c,kind==0?GREEN_BTN:kind==2?SURFACE:SAGE,kind==2?DANGER:0,14));button.setTextColor(kind==0?SURFACE:kind==2?DANGER:GREEN);button.setPadding(dp(c,12),dp(c,10),dp(c,12),dp(c,10));return button;}
    static Switch toggle(Switch toggle){toggle.setMinHeight(dp(toggle.getContext(),56));toggle.setTextSize(16);toggle.setTextColor(TEXT);int[][] states={{android.R.attr.state_checked},{}};toggle.setThumbTintList(new android.content.res.ColorStateList(states,new int[]{GREEN_BTN,SURFACE}));toggle.setTrackTintList(new android.content.res.ColorStateList(states,new int[]{GREEN_BTN,LINE}));toggle.setPadding(0,dp(toggle.getContext(),6),0,dp(toggle.getContext(),6));return toggle;}
    static TextView text(TextView view){view.setTextSize(16);view.setTextColor(TEXT);view.setPadding(0,dp(view.getContext(),5),0,dp(view.getContext(),5));return view;}
    static TextView note(TextView view){view.setTextSize(13);view.setTextColor(MUTED);return view;}
    static TextView heading(TextView view){view.setTextSize(23);view.setTextColor(GREEN);view.setTypeface(null,Typeface.BOLD);return view;}
    static void chip(TextView view,int kind){Context c=view.getContext();view.setTextSize(13);view.setTypeface(null,Typeface.BOLD);view.setTextColor(kind==0?GREEN:kind==1?INFO_TEXT:WARN_TEXT);view.setBackground(shape(c,kind==0?SAGE:kind==1?INFO_BG:WARN_BG,0,999));view.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6));view.setLayoutParams(new android.widget.LinearLayout.LayoutParams(-2,-2));}
    static void warning(TextView view){Context c=view.getContext();view.setTextColor(WARN_TEXT);view.setBackground(shape(c,WARN_BG,0,14));view.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));}
}
