package top.scraft.picman2.activity.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import top.scraft.picman2.R;
import top.scraft.picman2.storage.PicmanStorage;
import top.scraft.picman2.storage.dao.PictureLibrary;
import top.scraft.picman2.storage.dao.gen.PiclibPictureMapDao;

public class PiclibInfoAdapter extends BaseAdapter {

    private final Context context;
    private final PictureLibrary library;
    private final ArrayList<String> content = new ArrayList<>();

    public PiclibInfoAdapter(Context context, PictureLibrary library) {
        this.context = context;
        this.library = library;
        content.add("ID=" + (library.getOffline() ? "N/A" : library.getLid()));
        content.add("图库名=" + library.getName());
        long pictureCount = PicmanStorage.getInstance(context).getDaoSession().getPiclibPictureMapDao().queryBuilder()
                .where(PiclibPictureMapDao.Properties.AppInternalLid.eq(library.getAppInternalLid())).count();
        content.add("图片数=" + pictureCount);
    }

    @Override
    public int getCount() {
        return content.size();
    }

    @Override
    public String getItem(int i) {
        return content.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View convertView, ViewGroup viewGroup) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.item_piclib_info, null);
        }
        if (convertView != null) {
            String keyValue = getItem(i);
            TextView key = convertView.findViewById(R.id.piclib_info_key);
            TextView value = convertView.findViewById(R.id.piclib_info_value);
            key.setText(keyValue.substring(0, keyValue.indexOf("=")));
            value.setText(keyValue.substring(keyValue.indexOf("=") + 1));
        }
        return convertView;
    }

}
