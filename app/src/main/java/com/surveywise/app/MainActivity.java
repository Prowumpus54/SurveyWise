package com.surveywise.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * First runnable vertical slice: import floor plan, drop numbered photo pins,
 * capture full-resolution photo via system camera, dictate/edit text, persist locally.
 * DOCX and annotation tools remain planned, not claimed as implemented.
 */
public final class MainActivity extends Activity {
    private static final int PICK_PLAN = 10, CAMERA = 11, SPEECH = 12;
    private final ArrayList<Observation> items = new ArrayList<>();
    private Uri planUri, pendingPhoto;
    private PlanView planView;
    private TextView info;
    private EditText note;
    private Observation selected;
    private String project = "New Survey";
    private int page = 0;

    static final class Observation {
        int page;
        float x, y; // normalized page coordinates
        String image = "", note = "";
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        restore();
        buildUi();
        render();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12,12,12,12);
        EditText title = new EditText(this);
        title.setSingleLine(true);
        title.setHint("Survey project");
        title.setText(project);
        title.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int c,int f){}
            public void onTextChanged(CharSequence s,int st,int before,int count){project=s.toString();save();}
            public void afterTextChanged(android.text.Editable e){}
        });
        root.addView(title);
        LinearLayout controls = new LinearLayout(this);
        Button load=btn("Open plan", ()->pickPlan());
        Button prev=btn("◀", ()->{if(page>0){page--;render();}});
        Button next=btn("▶", ()->{page++;render();});
        Button photo=btn("📷 Photo", ()->capture());
        controls.addView(load,new LinearLayout.LayoutParams(0,-2,2));
        controls.addView(prev,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(next,new LinearLayout.LayoutParams(0,-2,1));
        controls.addView(photo,new LinearLayout.LayoutParams(0,-2,2));
        root.addView(controls);
        info = new TextView(this);
        root.addView(info);
        planView=new PlanView();
        root.addView(planView,new LinearLayout.LayoutParams(-1,0,1));
        note = new EditText(this);
        note.setMinLines(2);
        note.setHint("Tap plan to place a pin, then take a photo and dictate a note");
        root.addView(note);
        LinearLayout bottom=new LinearLayout(this);
        bottom.addView(btn("🎙 Dictate",()->dictate()),new LinearLayout.LayoutParams(0,-2,1));
        bottom.addView(btn("Save note",()->saveNote()),new LinearLayout.LayoutParams(0,-2,1));
        bottom.addView(btn("View photo",()->viewPhoto()),new LinearLayout.LayoutParams(0,-2,1));
        root.addView(bottom);
        setContentView(root);
    }

    private Button btn(String name,Runnable fn){
        Button b=new Button(this); b.setText(name); b.setAllCaps(false); b.setOnClickListener(v->fn.run());return b;
    }
    private void pickPlan(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/pdf").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,PICK_PLAN);
    }
    private void capture(){
        if(selected==null){toast("Tap the plan to place a pin first");return;}
        android.content.ContentValues v=new android.content.ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME,"surveywise_"+System.currentTimeMillis()+".jpg");
        v.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/SurveyWise");
        pendingPhoto=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,v);
        if(pendingPhoto==null){toast("Could not create photo");return;}
        Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT,pendingPhoto);
        i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivityForResult(i,CAMERA);}
        catch(Exception ex){getContentResolver().delete(pendingPhoto,null,null);pendingPhoto=null;toast("Camera not available");}
    }
    private void dictate(){
        if(selected==null){toast("Select a pin first");return;}
        Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Describe your observation");
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true);
        try{startActivityForResult(i,SPEECH);}catch(Exception ex){toast("Speech recognition unavailable; type a note");}
    }
    private void saveNote(){
        if(selected==null){toast("Select a pin first");return;}
        selected.note=note.getText().toString();save();toast("Observation saved");
    }
    private void viewPhoto(){
        if(selected==null||selected.image.isEmpty()){toast("No photo linked to this pin");return;}
        Intent i=new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(Uri.parse(selected.image),"image/jpeg");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try{startActivity(i);}catch(Exception ex){toast("Photo could not be opened");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==PICK_PLAN&&result==RESULT_OK&&data!=null){
            planUri=data.getData();
            getContentResolver().takePersistableUriPermission(planUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            items.clear();selected=null;page=0;save();render();
        } else if(request==CAMERA){
            if(result==RESULT_OK&&pendingPhoto!=null&&selected!=null){
                selected.image=pendingPhoto.toString();save();render();dictate();
            }else if(pendingPhoto!=null){getContentResolver().delete(pendingPhoto,null,null);}
            pendingPhoto=null;
        } else if(request==SPEECH&&result==RESULT_OK&&data!=null&&selected!=null){
            ArrayList<String> text=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if(text!=null&&!text.isEmpty()){
                String earlier=note.getText().toString();
                note.setText(earlier.isEmpty()?text.get(0):earlier+" "+text.get(0));
                selected.note=note.getText().toString();save();
            }
        }
    }
    private void render(){
        info.setText("Sheet "+(page+1)+" • "+items.size()+" observations • Tap drawing to place pin");
        if(planView!=null)planView.invalidate();
    }
    private void select(Observation o){selected=o;note.setText(o.note);render();}
    private File stateFile(){return new File(getFilesDir(),"survey.json");}
    private void save(){
        try{
            JSONObject data=new JSONObject();data.put("project",project);data.put("plan",planUri==null?"":planUri.toString());
            JSONArray a=new JSONArray();
            for(Observation o:items){JSONObject row=new JSONObject();row.put("page",o.page);row.put("x",o.x);row.put("y",o.y);row.put("image",o.image);row.put("note",o.note);a.put(row);}
            data.put("items",a);
            File temp=new File(getFilesDir(),"survey.tmp");
            try(FileOutputStream out=new FileOutputStream(temp)){out.write(data.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));out.getFD().sync();}
            if(!temp.renameTo(stateFile()))throw new IOException("Rename failed");
        }catch(Exception ex){android.util.Log.e("SurveyWise","Save failed",ex);}
    }
    private void restore(){
        try{
            File f=stateFile();if(!f.exists())return;
            byte[] bytes=new byte[(int)f.length()];
            try(FileInputStream in=new FileInputStream(f)){int off=0,n;while(off<bytes.length&&(n=in.read(bytes,off,bytes.length-off))!=-1)off+=n;}
            JSONObject data=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
            project=data.optString("project","New Survey");
            String uri=data.optString("plan");if(!uri.isEmpty())planUri=Uri.parse(uri);
            JSONArray a=data.optJSONArray("items");
            if(a!=null)for(int j=0;j<a.length();j++){JSONObject r=a.getJSONObject(j);Observation o=new Observation();o.page=r.optInt("page");o.x=(float)r.optDouble("x");o.y=(float)r.optDouble("y");o.image=r.optString("image");o.note=r.optString("note");items.add(o);}
        }catch(Exception e){android.util.Log.e("SurveyWise","Restore failed",e);}
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}

    private final class PlanView extends View {
        final Paint paint=new Paint(3);
        Bitmap bitmap;int cached=-1;
        PlanView(){super(MainActivity.this);setBackgroundColor(Color.rgb(242,244,246));}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);if(planUri==null){paint.setColor(Color.DKGRAY);paint.setTextSize(35);c.drawText("Open a PDF floor plan",20,70,paint);return;}
            if(cached!=page){if(bitmap!=null)bitmap.recycle();bitmap=null;cached=page;
                try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(planUri,"r");PdfRenderer renderer=new PdfRenderer(fd)){
                    if(page>=renderer.getPageCount()){page=renderer.getPageCount()-1;cached=page;}
                    if(page>=0)try(PdfRenderer.Page p=renderer.openPage(page)){
                        int w=Math.max(1,Math.min(1600,p.getWidth()));int h=Math.max(1,(int)((long)w*p.getHeight()/p.getWidth()));
                        bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                        bitmap.eraseColor(Color.WHITE);p.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    }
                }catch(Exception e){android.util.Log.e("SurveyWise","PDF render failed",e);}
            }
            if(bitmap==null)return;
            RectF area=area();
            c.drawBitmap(bitmap,null,area,paint);
            for(int i=0;i<items.size();i++){Observation o=items.get(i);if(o.page!=page)continue;
                float x=area.left+o.x*area.width(),y=area.top+o.y*area.height();
                paint.setColor(o==selected?Color.rgb(11,105,219):Color.rgb(223,80,48));
                c.drawCircle(x,y,16,paint);
                paint.setColor(Color.WHITE);paint.setTextSize(17);paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(""+(i+1),x,y+6,paint);
            }
            paint.setTextAlign(Paint.Align.LEFT);
        }
        RectF area(){
            if(bitmap==null)return new RectF(0,0,getWidth(),getHeight());
            float scale=Math.min(getWidth()/(float)bitmap.getWidth(),getHeight()/(float)bitmap.getHeight());
            float w=bitmap.getWidth()*scale,h=bitmap.getHeight()*scale;
            return new RectF((getWidth()-w)/2,(getHeight()-h)/2,(getWidth()+w)/2,(getHeight()+h)/2);
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent e){
            if(e.getAction()!=MotionEvent.ACTION_UP)return true;
            if(bitmap==null)return true;
            RectF a=area();if(!a.contains(e.getX(),e.getY()))return true;
            float x=(e.getX()-a.left)/a.width(),y=(e.getY()-a.top)/a.height();
            for(Observation o:items)if(o.page==page&&Math.hypot((o.x-x)*a.width(),(o.y-y)*a.height())<28){select(o);return true;}
            Observation o=new Observation();o.page=page;o.x=x;o.y=y;items.add(o);select(o);save();return true;
        }
    }
}
