package com.talentica.rowingapp.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.talentica.rowingapp.R;

/**
 * Created by suyashg on 20/04/17.
 */

public class PreferencesActivity extends PreferenceActivity {


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        ListView listView = getListView();
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {

                ListView listView = (ListView) parent;
                ListAdapter listAdapter = listView.getAdapter();
                Object obj = listAdapter.getItem(position);

                if (obj != null && obj instanceof Preference) {
                    Preference p = (Preference) obj;
                    String key = p.getKey();

                    if (key != null && key.startsWith("com.talentica.rowing") && !key.startsWith("com.talentica.rowing.android")) {

                        String url = "http://nargila.org/trac/robostroke/wiki/GuideParameters#" + key;
                        Intent i = new Intent(Intent.ACTION_VIEW);
                        i.setData(Uri.parse(url));
                        startActivity(i);
                    }

                    return true;
                }

                return false;
            }
        });
    }

}
