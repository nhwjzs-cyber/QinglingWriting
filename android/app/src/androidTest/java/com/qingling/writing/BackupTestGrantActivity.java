package com.qingling.writing;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.io.File;
import java.io.IOException;

public class BackupTestGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        if (getIntent().getBooleanExtra("revoke", false)) revokeUriPermission(BackupTestDocumentsProvider.TREE, flags);
        else {
            File marker = new File(getFilesDir(), "fail-backup-read");
            try {
                if (getIntent().getBooleanExtra("failRead", false)) marker.createNewFile(); else marker.delete();
            } catch (IOException e) { throw new IllegalStateException(e); }
            startActivity(new Intent().setClassName("com.qingling.writing", "com.qingling.writing.MainActivity")
                .setData(BackupTestDocumentsProvider.TREE).addFlags(flags | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
    }
}
