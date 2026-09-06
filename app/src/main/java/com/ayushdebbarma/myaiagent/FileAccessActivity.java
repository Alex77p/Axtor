package com.ayushdebbarma.myaiagent;

import android.app.*;import android.content.*;import android.net.Uri;import android.os.*;import android.widget.Toast;

/** Invokes Android's system picker so the owner can grant Axtor access to one chosen folder tree. */
public class FileAccessActivity extends Activity{
  private static final int PICK=901;
  @Override public void onCreate(Bundle b){super.onCreate(b);Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK);}
  @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==PICK&&c==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{int f=data.getFlags()&(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);getContentResolver().takePersistableUriPermission(u,f);}catch(Exception ignored){}FileAgentTools.saveTree(this,u);Toast.makeText(this,"Axtor file access granted",Toast.LENGTH_SHORT).show();}finish();}
}
