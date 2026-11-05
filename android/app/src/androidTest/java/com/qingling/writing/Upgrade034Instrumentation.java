package com.qingling.writing;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** Android-only runner: the supplied R8 APK does not expose Kotlin test dependencies. */
public class Upgrade034Instrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) {super.onCreate(args);start();}
    private List<AccessibilityNodeInfo> nodes(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> result=new ArrayList<>();if(root==null)return result;
        result.add(root);for(int i=0;i<root.getChildCount();i++)result.addAll(nodes(root.getChild(i)));return result;
    }
    private AccessibilityNodeInfo waitNode(String text, boolean editable) {
        for(int i=0;i<100;i++) {
            for(AccessibilityNodeInfo n:nodes(getUiAutomation().getRootInActiveWindow())) {
                if(editable && n.isEditable())return n;
                if(!editable && text.contentEquals(n.getText()==null?"":n.getText()))return n;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing node: "+text);
    }
    private void click(AccessibilityNodeInfo n){while(!n.isClickable()&&n.getParent()!=null)n=n.getParent();if(!n.performAction(AccessibilityNodeInfo.ACTION_CLICK))throw new AssertionError("click failed");SystemClock.sleep(300);}
    private void setText(AccessibilityNodeInfo n,String text){Bundle args=new Bundle();args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);if(!n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args))throw new AssertionError("set text failed");}
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            getUiAutomation();
            startActivitySync(new Intent().setClassName("com.qingling.writing","com.qingling.writing.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            click(waitNode("＋ 创建一本书",false));
            setText(waitNode("",true),"升级验收·原稿");
            click(waitNode("确定",false));
            SystemClock.sleep(1000);
            setText(waitNode("",true),"覆盖升级验收：原稿完整保留😀\n\n夜里两点，灯塔的光扫过海面。  ");
            SystemClock.sleep(2500);
            getTargetContext().getSharedPreferences("design035-upgrade",0).edit().putBoolean("seededFromOriginalApk",true).commit();
            result.putString("stream","Seeded original 0.34.0 APK through its UI; original manuscript saved.\n");finish(Activity.RESULT_OK,result);
        }catch(Throwable error){result.putString("stream",error.toString()+"\n");finish(Activity.RESULT_CANCELED,result);}
    }
}
