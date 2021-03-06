package top.scraft.picman2.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;
import com.tencent.smtt.sdk.QbSdk;

import org.greenrobot.greendao.query.CloseableListIterator;
import org.greenrobot.greendao.query.QueryBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import top.scraft.picman2.R;
import top.scraft.picman2.activity.adapter.SearchAdapter;
import top.scraft.picman2.server.ServerController;
import top.scraft.picman2.server.data.LibDetails;
import top.scraft.picman2.server.data.LibContentDetails;
import top.scraft.picman2.server.data.PictureDetail;
import top.scraft.picman2.storage.PicmanStorage;
import top.scraft.picman2.storage.dao.PiclibPictureMap;
import top.scraft.picman2.storage.dao.Picture;
import top.scraft.picman2.storage.dao.PictureLibrary;
import top.scraft.picman2.storage.dao.PictureTag;
import top.scraft.picman2.storage.dao.gen.DaoSession;
import top.scraft.picman2.storage.dao.gen.PiclibPictureMapDao;
import top.scraft.picman2.storage.dao.gen.PictureDao;
import top.scraft.picman2.storage.dao.gen.PictureLibraryDao;
import top.scraft.picman2.storage.dao.gen.PictureTagDao;

public class MainActivity extends AppCompatActivity implements TextWatcher {
  
  public static final int ACTIVITY_RESULT_LOGIN = 100;
  
  private SearchAdapter searchAdapter;
  private final List<Picture> searchResults = new ArrayList<>();
  
  private MenuItem syncMenuItem;
  private MenuItem systemMenuItem;
  private MenuItem loginMenuItem;
  private MenuItem logoutMenuItem;
  
  private TextInputEditText inputSearch;
  
  private ServerController serverController;
  private PicmanStorage picmanStorage;
  private boolean syncRotating = false;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    // find views
    inputSearch = findViewById(R.id.inputSearch);
    inputSearch.addTextChangedListener(this);
    RecyclerView recyclerView = findViewById(R.id.main_gallery);
    // get controllers
    serverController = ServerController.getInstance(getApplicationContext());
    picmanStorage = PicmanStorage.getInstance(getApplicationContext());
    // init
    QbSdk.initX5Environment(getApplicationContext(), null);
    searchAdapter = new SearchAdapter(this, searchResults);
    recyclerView.setLayoutManager(new GridLayoutManager(this, 4, RecyclerView.VERTICAL, false));
    recyclerView.setAdapter(searchAdapter);
    requestPermissions();
    syncMetadata();
  }
  
  private void syncMetadata() {
    setSyncMenuItemRotation(true);
    if (loginMenuItem != null) {
      systemMenuItem.setVisible(false);
      loginMenuItem.setVisible(false);
      logoutMenuItem.setVisible(false);
    }
    new Thread(() -> {
      if (serverController.updateState()) {
        if (serverController.getState().login) {
          runOnUiThread(() -> {
            new Thread(() -> {
              // ???????????????, ???????????????dao
              List<LibDetails> libDetailsListServer = serverController.getPiclibs();
              DaoSession daoSession = picmanStorage.getDaoSession();
              PictureLibraryDao lDao = daoSession.getPictureLibraryDao();
              PictureDao pDao = daoSession.getPictureDao();
              PictureTagDao tDao = daoSession.getPictureTagDao();
              PiclibPictureMapDao lpmDao = daoSession.getPiclibPictureMapDao();
              // ?????????????????????????????????
              for (LibDetails libDetailsServer : libDetailsListServer) {
                // ????????????????????????
                PictureLibrary libraryLocal = lDao.queryBuilder().where(PictureLibraryDao.Properties.Lid.eq(libDetailsServer.getLid())).unique();
                if (libraryLocal != null && Long.valueOf(libDetailsServer.getLastUpdate()).equals(libraryLocal.getLastUpdate())) {
                  // ??????????????????????????????
                  continue;
                }
                // ???????????? ??????????????????
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "???????????? " + libDetailsServer.getName(), Toast.LENGTH_SHORT).show());
                // ????????????????????????
                List<LibContentDetails> libraryContentDetailsServer = serverController.getLibraryContentDetails(libDetailsServer.getLid());
                // ?????????????????????????????????
                if (libraryLocal == null) {
                  libraryLocal = new PictureLibrary();
                  libraryLocal.setLid(libDetailsServer.getLid());
                  libraryLocal.setName(libDetailsServer.getName());
                  libraryLocal.setLastUpdate(0L);
                  lDao.save(libraryLocal);
                }
                // ???????????????????????? (???????????????????????????????????????????????????????????????)
                List<String> serverValidPicturePids = new ArrayList<>();
                long time = libraryLocal.getLastUpdate();
                for (LibContentDetails libContentServer : libraryContentDetailsServer) {
                  if (!libContentServer.isValid()) {
                    // ?????????????????????????????????
                    continue;
                  }
                  String pid = libContentServer.getPid();
                  serverValidPicturePids.add(pid);
                  // ???????????????????????????????????????
                  if (libContentServer.getLastModify() > time) {
                    // ?????????????????????????????????
                    Picture pictureLocal = pDao.queryBuilder().where(PictureDao.Properties.Pid.eq(pid)).unique();
                    // ????????????????????????
                    PictureDetail pictureServer = serverController.getPictureMeta(pid).getData();
                    assert pictureServer != null; // FIXME: ????????????
                    // ????????????????????????
                    if (pictureLocal == null) {
                      pictureLocal = new Picture();
                      pictureLocal.setPid(pid);
                      pictureLocal.setCreateTime(pictureServer.getCreateTime());
                      pictureLocal.setFileSize(pictureServer.getFileSize());
                      pictureLocal.setHeight(pictureServer.getHeight());
                      pictureLocal.setWidth(pictureServer.getWidth());
                    }
                    // ??????????????????
                    pictureLocal.setDescription(pictureServer.getDescription());
                    pictureLocal.setLastModify(pictureServer.getLastModify());
                    pDao.insertOrReplace(pictureLocal);
                    // ?????????Tag
                    tDao.queryBuilder()
                        .where(PictureTagDao.Properties.AppInternalPid.eq(pictureLocal.getAppInternalPid()))
                        .buildDelete().executeDeleteWithoutDetachingEntities();
                    // ?????????Tag
                    for (String tag : pictureServer.getTags()) {
                      PictureTag t = new PictureTag();
                      t.setAppInternalPid(pictureLocal.getAppInternalPid());
                      t.setTag(tag);
                      tDao.insert(t);
                    }
                    // ??????????????????????????????????????????
                    if (lpmDao.queryBuilder().where(
                        PiclibPictureMapDao.Properties.AppInternalLid.eq(libraryLocal.getAppInternalLid()),
                        PiclibPictureMapDao.Properties.AppInternalPid.eq(pictureLocal.getAppInternalPid())
                    ).count() == 0) {
                      PiclibPictureMap lpMap = new PiclibPictureMap();
                      lpMap.setAppInternalLid(libraryLocal.getAppInternalLid());
                      lpMap.setAppInternalPid(pictureLocal.getAppInternalPid());
                      lpmDao.insert(lpMap);
                    }
                  }
                }
                // ??????????????????????????????????????????(????????????)?????????id
                ArrayList<Long> lpmDeleteImid = new ArrayList<>();
                QueryBuilder<PiclibPictureMap> lpmDeleteQuery = lpmDao.queryBuilder();
                lpmDeleteQuery.where(
                    PiclibPictureMapDao.Properties.AppInternalLid.eq(libraryLocal.getAppInternalLid())
                ).join(
                    PiclibPictureMapDao.Properties.AppInternalPid,
                    Picture.class,
                    PictureDao.Properties.AppInternalPid
                ).where(
                    PictureDao.Properties.Pid.notIn(serverValidPicturePids)
                );
                // ???????????????????????????????????????????????? (????????????!!)
                try (CloseableListIterator<PiclibPictureMap> iterator = lpmDeleteQuery.listIterator()) {
                  while (iterator.hasNext()) {
                    lpmDeleteImid.add(iterator.next().getAppInternalMapId());
                  }
                } catch (Exception e) {
                  e.printStackTrace();
                }
                lpmDao.queryBuilder().where(PiclibPictureMapDao.Properties.AppInternalMapId.in(lpmDeleteImid))
                    .buildDelete().executeDeleteWithoutDetachingEntities();
                // ??????????????????????????????
                libraryLocal.setLastUpdate(libDetailsServer.getLastUpdate());
                libraryLocal.update();
              }
              runOnUiThread(() -> setSyncMenuItemRotation(false));
            }).start();
            if (loginMenuItem != null) {
              systemMenuItem.setVisible(serverController.getUser().isAdmin());
//                            logoutMenuItem.setVisible(true); // TODO
            }
          });
        } else {
          runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, serverController.getState().message, Toast.LENGTH_SHORT).show();
            setSyncMenuItemRotation(false);
            if (loginMenuItem != null) {
              loginMenuItem.setVisible(true);
            }
          });
        }
      } else {
        // ??????????????????
        runOnUiThread(() -> {
          Toast.makeText(MainActivity.this, serverController.getState().message, Toast.LENGTH_SHORT).show();
          setSyncMenuItemRotation(false);
        });
      }
    }).start();
  }
  
  private void setSyncMenuItemRotation(boolean rotation) {
    syncRotating = rotation;
    if (syncMenuItem != null) {
      if (rotation) {
        Animation animation = AnimationUtils.loadAnimation(this, R.anim.icon_rotate);
        animation.setRepeatMode(Animation.RESTART);
        animation.setRepeatCount(Animation.INFINITE);
        ImageView refreshView = (ImageView) getLayoutInflater().inflate(R.layout.iconview_refresh, null);
        refreshView.setImageResource(R.drawable.ic_baseline_sync_24);
        syncMenuItem.setActionView(refreshView);
        refreshView.startAnimation(animation);
      } else {
        View view = syncMenuItem.getActionView();
        if (view != null) {
          view.clearAnimation();
          syncMenuItem.setActionView(null);
        }
      }
    }
  }
  
  @Override
  public void beforeTextChanged(CharSequence s, int start, int count, int after) {
  }
  
  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
  }
  
  @Override
  public void afterTextChanged(Editable s) {
    Editable searchText = inputSearch.getText();
    if (searchText != null) {
      String keyword = searchText.toString();
      if (keyword.isEmpty()) {
        searchResults.clear();
        searchAdapter.notifyDataSetChanged();
      } else {
        DaoSession daoSession = PicmanStorage.getInstance(getApplicationContext()).getDaoSession();
        PictureDao pDao = daoSession.getPictureDao();
        PictureTagDao tDao = daoSession.getPictureTagDao();
        String likePattern = "%" + searchText.toString() + "%";
        searchResults.clear();
        searchResults.addAll(pDao.queryBuilder().where(PictureDao.Properties.Description.like(likePattern)).list()); // FIXME ???????????????%?????????????
        // ????????????Tag?????????ipid
        Set<Long> tagMatchedInternalPids = new HashSet<>();
        for (PictureTag pictureTag : tDao.queryBuilder().where(PictureTagDao.Properties.Tag.like(likePattern)).list()) {
          tagMatchedInternalPids.add(pictureTag.getAppInternalPid());
        }
        // ???????????????ipid
        for (Picture picture : searchResults) {
          tagMatchedInternalPids.remove(picture.getAppInternalPid());
        }
        // ????????????
        searchResults.addAll(pDao.queryBuilder().where(PictureDao.Properties.AppInternalPid.in(tagMatchedInternalPids)).list());
      
        searchAdapter.notifyDataSetChanged();
        if (searchResults.size() == 0) {
          inputSearch.setError("??????????????????");
        } else {
          inputSearch.setError(null);
        }
      }
    }
  }
  
  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    if (requestCode == ACTIVITY_RESULT_LOGIN) {
      if (resultCode == Activity.RESULT_OK && data != null) {
        String host = data.getStringExtra("HOST");
        String pmst = data.getStringExtra("PMST");
//                Log.d("picman_pmst", pmst);
        if (pmst != null) {
          Toast.makeText(this, "????????????", Toast.LENGTH_SHORT).show();
          serverController.setPmst(host, pmst);
          syncMetadata();
        }
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
  
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);
    syncMenuItem = menu.findItem(R.id.menu_main_sync);
    systemMenuItem = menu.findItem(R.id.menu_main_system);
    loginMenuItem = menu.findItem(R.id.menu_main_login);
    logoutMenuItem = menu.findItem(R.id.menu_main_logout);
    systemMenuItem.setVisible(false);
    loginMenuItem.setVisible(false);
    logoutMenuItem.setVisible(false);
    setSyncMenuItemRotation(syncRotating);
    return true;
  }
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.menu_main_login) {
      String loginUrl = serverController.getServer().concat("/login");
      Intent intent = new Intent(this, BrowserActivity.class);
      intent.putExtra("URL", loginUrl);
      intent.putExtra("TYPE", "LOGIN");
      startActivityForResult(intent, ACTIVITY_RESULT_LOGIN);
    } else if (id == R.id.menu_main_logout) {
      new Thread(() -> {
        serverController.logout();
        runOnUiThread(this::syncMetadata);
      }).start();
    } else if (id == R.id.menu_main_settings) {
      startActivity(new Intent(this, SettingsActivity.class));
    } else if (id == R.id.menu_main_sync) {
      // TODO
    } else if (id == R.id.menu_main_piclib) {
      startActivity(new Intent(this, PicLibManagerActivity.class));
    } else if (id == R.id.menu_main_system) {
      Intent intent = new Intent(this, BrowserActivity.class);
      intent.putExtra("URL", serverController.getServer().concat("/#/admin"));
      startActivity(intent);
    }
    return false;
  }
  
  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == 100 && grantResults.length == 2) {
      for (int v : grantResults) {
        if (v != PackageManager.PERMISSION_GRANTED) {
          Toast.makeText(this, "???????????????????????????,????????????", Toast.LENGTH_LONG).show();
          finish();
        }
      }
    }
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }
  
  private void requestPermissions() {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission_group.STORAGE) != PackageManager.PERMISSION_GRANTED) {
      String[] perms = {
          Manifest.permission.READ_EXTERNAL_STORAGE,
          Manifest.permission.WRITE_EXTERNAL_STORAGE
      };
      ActivityCompat.requestPermissions(this, perms, 100);
    }
  }
  
}
