package top.scraft.picman2.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

import top.scraft.picman2.R;
import top.scraft.picman2.activity.adapter.ArrangeAdapter;

public class ArrangeActivity extends AppCompatActivity {

    private final ArrangeAdapter adapter = new ArrangeAdapter(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_arrange);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission_group.STORAGE) != PackageManager.PERMISSION_GRANTED) {
            String[] perms = {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, perms, 100);
        } else {
            load();
        }
    }

    private void load() {
        // 初始化视图
        RecyclerView recyclerArrange = findViewById(R.id.recycler_arrange);
        recyclerArrange.setLayoutManager(new GridLayoutManager(this, 4, RecyclerView.VERTICAL, false));
        recyclerArrange.setAdapter(adapter);
        if (!parseShareIntent()) {
            Toast.makeText(this, "没有接收到有效的图片分享", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private boolean parseShareIntent() {
        Intent intent = getIntent();
        if (intent != null) {
            String action = intent.getAction();
            ArrayList<Uri> uriList;
            if (Intent.ACTION_SEND.equals(action)) {
                uriList = new ArrayList<>();
                uriList.add(intent.getParcelableExtra(Intent.EXTRA_STREAM));
            } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
                uriList = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            } else {
                return false;
            }
            if (uriList == null) {
                return false;
            }
            adapter.getPictureUriList().addAll(uriList);
            adapter.notifyDataSetChanged();
            // TODO 是不是可以直接用编辑活动接收分享
//            if (pathList.size() == 1) {
//                // 只有一张图片时候直接打开编辑界面
//                Intent editIntent = new Intent();
//                editIntent.setClass(this, PictureEditorActivity.class);
//                editIntent.putExtra("file", pathList.get(0));
//                startActivity(editIntent);
//            }
            return uriList.size() > 0;
        } else {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 100 && grantResults.length == 2) {
            for (int v : grantResults) {
                if (v != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "未授权存储读写权限,无法使用", Toast.LENGTH_LONG).show();
                    finish();
                }
            }
            load();
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}