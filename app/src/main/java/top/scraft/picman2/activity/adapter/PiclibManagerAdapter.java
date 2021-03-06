package top.scraft.picman2.activity.adapter;

import android.app.ProgressDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.RequiredArgsConstructor;
import top.scraft.picman2.R;
import top.scraft.picman2.activity.PicLibManagerActivity;
import top.scraft.picman2.server.ServerController;
import top.scraft.picman2.server.data.LibDetails;
import top.scraft.picman2.server.data.PictureDetail;
import top.scraft.picman2.server.data.Result;
import top.scraft.picman2.storage.PicmanStorage;
import top.scraft.picman2.storage.PictureStorageController;
import top.scraft.picman2.storage.dao.PiclibPictureMap;
import top.scraft.picman2.storage.dao.Picture;
import top.scraft.picman2.storage.dao.PictureLibrary;
import top.scraft.picman2.storage.dao.gen.PiclibPictureMapDao;
import top.scraft.picman2.storage.dao.gen.PictureDao;
import top.scraft.picman2.storage.dao.gen.PictureLibraryDao;
import top.scraft.picman2.utils.Utils;

@RequiredArgsConstructor
public class PiclibManagerAdapter extends BaseAdapter {
  
  private final PicLibManagerActivity context;
  private final List<PictureLibrary> infoList;
  
  @Override
  public int getCount() {
    return infoList.size();
  }
  
  @Override
  public PictureLibrary getItem(int position) {
    return infoList.get(position);
  }
  
  @Override
  public long getItemId(int position) {
    return infoList.get(position).getAppInternalLid();
  }
  
  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    if (convertView == null) {
      convertView = LayoutInflater.from(context).inflate(R.layout.item_piclib, null);
    }
    if (convertView != null) {
      PictureLibrary library = getItem(position);
      TextView title = convertView.findViewById(R.id.piclib_title);
      String name = library.getName();
      if (library.getOffline()) {
        name = "[??????]" + name;
      }
      title.setText(name);
      convertView.setTag(library);
      convertView.setOnClickListener(this::showLibraryDialog);
    }
    return convertView;
  }
  
  private void showLibraryDialog(@NonNull View view) {
    Object o = view.getTag();
    if (o instanceof PictureLibrary) {
      PictureLibrary library = (PictureLibrary) o;
      AlertDialog.Builder dialog = new AlertDialog.Builder(context);
      View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_piclib_info, null);
      
      dialog.setTitle(library.getName());
      dialog.setView(dialogView);
      dialog.setNegativeButton(R.string.text_close, null);
      
      TabLayout tab = dialogView.findViewById(R.id.dialog_piclib_info_tabs);
      ViewPager pager = dialogView.findViewById(R.id.dialog_piclib_pager);
      tab.setupWithViewPager(pager, false);
      AlertDialog alertDialog = dialog.show();
      pager.setAdapter(new MyPagerAdapter(library, alertDialog));
    }
  }
  
  @RequiredArgsConstructor
  private class MyPagerAdapter extends PagerAdapter {
    private final String[] tabTitles = new String[]{"??????", "??????", "??????", "??????"};
    private final PictureLibrary library;
    private final AlertDialog superDialog;
    
    @Override
    public int getCount() {
      return tabTitles.length;
    }
    
    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
      return view == object;
    }
    
    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
      View ret = null;
      if (position == 0) {
        View v = LayoutInflater.from(context).inflate(R.layout.dialog_piclib_info_list, null);
        ListView listView = v.findViewById(R.id.dialog_piclib_info_list);
        listView.setAdapter(new PiclibInfoAdapter(context, library));
        ret = v;
      } else if (position == 1 || position == 2) {
        TextView textView = new TextView(context);
        textView.setText("TODO");
        ret = textView;
      } else if (position == 3) {
        ListView listView = new ListView(context);
        List<Map<String, Object>> options = new ArrayList<>();
        Map<String, Object> map;
        
        map = new HashMap<>();
        map.put("option", "?????????" + (library.getOffline() ? "??????" : "??????"));
        options.add(map);
        
        map = new HashMap<>();
        map.put("option", "??????");
        options.add(map);
        
        SimpleAdapter simpleAdapter = new SimpleAdapter(context, options, android.R.layout.simple_list_item_1,
            new String[]{"option"}, new int[]{android.R.id.text1});
        listView.setAdapter(simpleAdapter);
        listView.setOnItemClickListener((parent, view1, position1, id) -> {
          final ServerController serverController = ServerController.getInstance(context.getApplicationContext());
          final PicmanStorage picmanStorage = PicmanStorage.getInstance(context.getApplicationContext());
          final PictureStorageController storageController = picmanStorage.getPictureStorage();
          final PictureLibraryDao lDao = picmanStorage.getDaoSession().getPictureLibraryDao();
          final PictureDao pDao = picmanStorage.getDaoSession().getPictureDao();
          final PiclibPictureMapDao lpmDao = picmanStorage.getDaoSession().getPiclibPictureMapDao();
          
          String selectedOption = (String) options.get(position1).get("option");
          if (selectedOption != null) {
            if ("??????".equals(selectedOption)) {
              AlertDialog.Builder confirm = new AlertDialog.Builder(context);
              confirm.setTitle("????????????");
              confirm.setMessage(String.format("??????????????????[%s]?", library.getName()));
              confirm.setNegativeButton(R.string.text_cancel, null);
              confirm.setPositiveButton(R.string.text_delete, (d, w) -> new Thread(() -> {
                // ?????????????????????????????????
                if (!library.getOffline()) {
                  String error = serverController.deleteLibrary(library.getLid());
                  if (error != null) {
                    context.runOnUiThread(() -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show());
                    return;
                  }
                }
                // ??????????????????
                lDao.deleteByKey(library.getAppInternalLid());
                Iterator<PictureLibrary> itr = infoList.iterator();
                while (itr.hasNext()) {
                  PictureLibrary l = itr.next();
                  if (l.getAppInternalLid().equals(library.getAppInternalLid())) {
                    itr.remove();
                    // ????????????
                    lpmDao.queryBuilder()
                        .where(PiclibPictureMapDao.Properties.AppInternalLid.eq(l.getAppInternalLid()))
                        .buildDelete().executeDeleteWithoutDetachingEntities();
                  }
                }
                context.runOnUiThread(PiclibManagerAdapter.this::notifyDataSetChanged);
                superDialog.dismiss();
              }).start());
              confirm.show();
            } else if (selectedOption.startsWith("?????????")) {
              if (library.getOffline()) {
                // ??????????????? ????????????????????????
                new AlertDialog.Builder(context)
                    .setTitle("???????????????")
                    .setMessage(String.format(Locale.CHINA,
                        "??????%s????????????, ??????????????????????????????????????????.\n" +
                            "!!!??????: ????????????, ?????????????????????.\n" +
                            "?????????????????????????????????????????????.\n" +
                            "??????????????????????????????????????????.",
                        library.getName()))
                    .setNegativeButton(R.string.text_cancel, null)
                    .setPositiveButton(R.string.text_confirm, (_d, _w) -> {
                      ProgressDialog progressDialog = new ProgressDialog(context);
                      progressDialog.setTitle(String.format(Locale.CHINA, "??????%s????????????", library.getName()));
                      progressDialog.setMessage("??????????????????");
                      progressDialog.setCancelable(false);
                      PictureLibrary oldLib = lDao.load(this.library.getAppInternalLid()); // ????????????Lib????????????Session ??????????????????
                      List<Picture> pictureList = oldLib.getPictures();
                      final int total = oldLib.getPictures().size() + 1;
                      final AtomicInteger progress = new AtomicInteger(0);
                      progressDialog.setMax(total);
                      progressDialog.setProgress(0);
                      progressDialog.show();
                      new Thread(() -> {
                        // ?????????????????????
                        Result<LibDetails> newLibDetails = serverController.createLibrary(oldLib.getName());
                        if (newLibDetails.getCode() != 200) {
                          String error = newLibDetails.getMessage();
                          context.runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show();
                          });
                          return;
                        }
                        PictureLibrary newLib = new PictureLibrary();
                        assert (newLibDetails.getData() != null);
                        newLib.setLid(newLibDetails.getData().getLid());
                        newLib.setName(newLibDetails.getData().getName());
                        newLib.setLastUpdate(newLibDetails.getData().getLastUpdate());
                        lDao.save(newLib);
                        context.runOnUiThread(context::updateData);
                        while (progress.get() < pictureList.size()) {
                          int p = progress.incrementAndGet();
                          Picture picture = pDao.load(pictureList.get(p - 1).getAppInternalPid());
                          context.runOnUiThread(() -> {
                            progressDialog.setProgress(p);
                            progressDialog.setMessage("???????????? " + picture.getDescription());
                          });
                          Result<PictureDetail> serverPictureDetailResult = serverController.getPictureMeta(picture.getPid());
                          boolean needUpload;
                          if (serverPictureDetailResult.getCode() == 404) {
                            // ?????????
                            Result<PictureDetail> newPicDetail = serverController.updatePictureMeta(newLib.getLid(),
                                picture.getPid(), picture.getDescription(), picture.getTagsAsStringSet());
                            if (newPicDetail.getCode() == 200 && newPicDetail.getData() != null) {
                              needUpload = !newPicDetail.getData().isValid();
                            } else {
                              // ???????????? ???????????????
                              context.runOnUiThread(() -> {
                                progressDialog.dismiss();
                                Toast.makeText(context, "????????????: " +
                                    serverPictureDetailResult.getMessage(), Toast.LENGTH_SHORT).show();
                              });
                              return; // ???????????????????????????
                            }
                          } else if (serverPictureDetailResult.getCode() == 200 && serverPictureDetailResult.getData() != null) {
                            // ?????? ??????
                            needUpload = !serverPictureDetailResult.getData().isValid();
                          } else {
                            // ????????????
                            context.runOnUiThread(() -> Toast.makeText(context,
                                serverPictureDetailResult.getMessage(), Toast.LENGTH_SHORT).show());
                            continue;
                          }
                          if (needUpload) {
                            byte[] bytes = null;
                            try {
                              File file = storageController.getPicturePath(picture.getPid());
                              FileInputStream fileInputStream = new FileInputStream(file);
                              bytes = Utils.readStream(fileInputStream);
                              fileInputStream.close();
                            } catch (IOException e) {
                              e.printStackTrace();
                              context.runOnUiThread(() -> Toast.makeText(context, "?????????????????? " + e.getLocalizedMessage(), Toast.LENGTH_SHORT).show());
                            }
                            if (bytes != null) {
                              Result<Object> uploadResult = serverController.uploadPicture(newLib.getLid(), picture.getPid(), bytes);
                              if (uploadResult.getCode() != 200) {
                                context.runOnUiThread(() -> Toast.makeText(context, "????????????: " +
                                    uploadResult.getMessage(), Toast.LENGTH_SHORT).show());
                              }
                            }
                          }
                          // ???????????? ???????????????
                          PiclibPictureMap lpMap = new PiclibPictureMap(null,
                              newLib.getAppInternalLid(), picture.getAppInternalPid());
                          lpmDao.save(lpMap);
                        }
                        context.runOnUiThread(() -> {
                          progressDialog.dismiss();
                          context.updateData();
                          Toast.makeText(context, "????????????", Toast.LENGTH_SHORT).show();
                        });
                      }).start();
                      superDialog.dismiss();
                    })
                    .show();
              } else {
                // ??????????????? ?????????????????????
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("???????????????");
                builder.setMessage(String.format(Locale.CHINA,
                    "??????%s???????????????, ???????????????????????????????????????.",
                    library.getName()));
                builder.setNegativeButton(R.string.text_cancel, null);
                builder.setPositiveButton(R.string.text_confirm, (d, w) -> new Thread(() -> {
                  String error = serverController.deleteLibrary(library.getLid());
                  if (error != null) {
                    context.runOnUiThread(() -> Toast.makeText(context, error, Toast.LENGTH_SHORT).show());
                    return;
                  }
                  library.setOffline(true);
                  lDao.save(library);
                  context.runOnUiThread(() -> {
                    Toast.makeText(context, "????????????", Toast.LENGTH_SHORT).show();
                    context.updateData();
                  });
                  superDialog.dismiss();
                }).start());
                builder.show();
              }
            }
          }
        });
        ret = listView;
      }
      Objects.requireNonNull(ret);
      container.addView(ret);
      return ret;
    }
    
    @Override
    public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
      container.removeView((View) object);
    }
    
    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
      return tabTitles[position];
    }
  }
  
}
