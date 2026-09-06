package com.ayushdebbarma.myaiagent;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.Toast;
import java.util.Locale;

/** Opens Android's All files access control when available, then falls back to a scoped folder workspace. */
public class FileAccessActivity extends Activity {
  private static final int ALL_FILES = 902;
  private static final int PICK = 901;

  @Override public void onCreate(Bundle b) {
    super.onCreate(b);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !android.os.Environment.isExternalStorageManager()) {
      try {
        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + getPackageName()));
        startActivityForResult(i, ALL_FILES);
        return;
      } catch (Exception ignored) {}
    }
    openWorkspacePicker();
  }

  @Override protected void onActivityResult(int r, int c, Intent data) {
    super.onActivityResult(r, c, data);
    if (r == ALL_FILES) {
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || android.os.Environment.isExternalStorageManager()) {
        Toast.makeText(this, "Axtor: all files access enabled", Toast.LENGTH_SHORT).show();
      } else {
        Toast.makeText(this, "All files access was not enabled; choose a workspace instead", Toast.LENGTH_LONG).show();
      }
      openWorkspacePicker();
      return;
    }
    if (r == PICK && c == RESULT_OK && data != null && data.getData() != null) {
      Uri u = data.getData();
      try {
        int f = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        getContentResolver().takePersistableUriPermission(u, f);
      } catch (Exception ignored) {}
      FileAgentTools.saveTree(this, u);
      Toast.makeText(this, "Axtor file workspace granted", Toast.LENGTH_SHORT).show();
    }
    finish();
  }

  private void openWorkspacePicker() {
    try {
      Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
      i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
      startActivityForResult(i, PICK);
    } catch (Exception e) {
      Toast.makeText(this, "Android file picker unavailable", Toast.LENGTH_LONG).show();
      finish();
    }
  }
}
