package com.trianguloy.urlchecker.modules.list;

import static com.trianguloy.urlchecker.utilities.JavaUtils.valueOrDefault;

import android.app.AlertDialog;
import android.content.Context;
import android.text.Editable;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.trianguloy.urlchecker.R;
import com.trianguloy.urlchecker.activities.ModulesActivity;
import com.trianguloy.urlchecker.dialogs.MainDialog;
import com.trianguloy.urlchecker.modules.AModuleConfig;
import com.trianguloy.urlchecker.modules.AModuleData;
import com.trianguloy.urlchecker.modules.AModuleDialog;
import com.trianguloy.urlchecker.modules.companions.Flags;
import com.trianguloy.urlchecker.url.UrlData;
import com.trianguloy.urlchecker.utilities.AndroidUtils;
import com.trianguloy.urlchecker.utilities.DefaultTextWatcher;
import com.trianguloy.urlchecker.utilities.Enums;
import com.trianguloy.urlchecker.utilities.GenericPref;
import com.trianguloy.urlchecker.utilities.Inflater;
import com.trianguloy.urlchecker.utilities.InternalFile;
import com.trianguloy.urlchecker.utilities.JavaUtils;
import com.trianguloy.urlchecker.views.CycleImageButton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This module allows flag edition
 */
public class FlagsModule extends AModuleData {

    public static GenericPref.Str DEFAULTFLAGS_PREF(Context cntx) {
        return new GenericPref.Str("flagsEditor_defaultFlags", null, cntx);
    }

    public static final String DEFAULT_GROUP = "default";

    @Override
    public String getId() {
        return "flagsEditor";
    }

    @Override
    public int getName() {
        return R.string.mFlags_name;
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public AModuleDialog getDialog(MainDialog cntx) {
        return new FlagsDialog(cntx);
    }

    @Override
    public AModuleConfig getConfig(ModulesActivity cntx) {
        return new FlagsConfig(cntx);
    }
}

class FlagsDialog extends AModuleDialog {

    private final Flags defaultFlags;
    private final Flags currentFlags;

    private Map<String, FlagsConfig.FlagState> flagsStatePref;

    private ViewGroup shownFlagsVG;
    private EditText searchInput;
    private ViewGroup hiddenFlagsVG;
    private ImageView overflowButton;

    private JSONObject groups;

    public FlagsDialog(MainDialog dialog) {
        super(dialog);
        defaultFlags = new Flags(getActivity().getIntent().getFlags());
        currentFlags = new Flags();
    }

    @Override
    public int getLayoutId() {
        return R.layout.dialog_flags;
    }

    @Override
    public void onInitialize(View views) {
        initGroups();

        shownFlagsVG = views.findViewById(R.id.shownFlags);
        searchInput = views.findViewById(R.id.search);
        hiddenFlagsVG = views.findViewById(R.id.hiddenFlags);

        // Button to open the `box` with the hidden flags (more indicator)
        overflowButton = views.findViewById(R.id.overflowButton);

        // Hide hidden flags
        hiddenFlagsVG.setVisibility(View.GONE);
        AndroidUtils.toggleableListener(overflowButton,
                v -> hiddenFlagsVG.setVisibility(hiddenFlagsVG.getVisibility() == View.GONE ? View.VISIBLE : View.GONE),
                v -> {
                    searchInput.setVisibility(hiddenFlagsVG.getVisibility());
                    updateMoreIndicator();
                });

        // SEARCH
        // Set up search text
        searchInput.addTextChangedListener(new DefaultTextWatcher() {
            @Override
            public void afterTextChanged(Editable text) {
                for (int i = 0; i < hiddenFlagsVG.getChildCount(); i++) {
                    var checkbox_text = hiddenFlagsVG.getChildAt(i);
                    String flag = ((TextView) checkbox_text.findViewById(R.id.text)).getText().toString();
                    String search = text.toString();
                    // Set visibility based on search text
                    checkbox_text.setVisibility(JavaUtils.containsWords(flag, search) ? View.VISIBLE : View.GONE);
                }
            }
        });

        // TODO spinner with groups
        loadGroup(FlagsModule.DEFAULT_GROUP);
    }

    private void initGroups() {
        String fileString = new InternalFile(FlagsConfig.CONF_FILE, getActivity()).get();
        groups = new JSONObject();
        if (fileString != null) {
            try {
                groups = new JSONObject(fileString).getJSONObject("groups");
            } catch (JSONException ignore) {
            }
        }
    }

    // To get all the groups names
    private List<String> getGroups() {
        List<String> res = new ArrayList<>();
        // Always add FlagsModule.DEFAULT_GROUP first, even if it doesn't exist
        res.add(FlagsModule.DEFAULT_GROUP);
        for (Iterator<String> it = groups.keys(); it.hasNext(); ) {
            String group = it.next();
            if (!group.equals(FlagsModule.DEFAULT_GROUP)) {
                res.add(group);
            }
        }
        return res;
    }

    void loadGroup(String group) {
        currentFlags.setFlags(0);

        // Load json
        JSONObject groupPref = null;
        try {
            groupPref = groups.getJSONObject(group);
        } catch (JSONException ignore) {
        }


        // STATE
        // Get state preference of flag from json and then store it in a map
        flagsStatePref = new HashMap<>();
        if (groupPref != null) {
            Map<Integer, FlagsConfig.FlagState> flagsStateMap = Enums.toEnumMap(FlagsConfig.FlagState.class);
            for (Iterator<String> it = groupPref.keys(); it.hasNext(); ) {
                String flag = it.next();
                try {
                    flagsStatePref.put(flag, flagsStateMap.get(groupPref.getJSONObject(flag).getInt("state")));
                } catch (JSONException ignored) {
                }
            }
        }

        // SHOW
        // Put shown flags
        Set<String> shownFlagsSet = new TreeSet<>();
        if (groupPref != null) {
            for (Iterator<String> it = groupPref.keys(); it.hasNext(); ) {
                String flag = it.next();
                try {
                    if (groupPref.getJSONObject(flag).getBoolean("show")) {
                        shownFlagsSet.add(flag);
                    }
                } catch (JSONException ignored) {
                }
            }
        }

        // If it is not in shownFlags it must be in hiddenFlags
        Set<String> hiddenFlagsSet = new TreeSet<>(Flags.getCompatibleFlags().keySet());
        hiddenFlagsSet.removeAll(shownFlagsSet);

        // Fill boxes with flags, load flags into currentFlags too
        fillWithFlags(shownFlagsSet, shownFlagsVG);
        fillWithFlags(hiddenFlagsSet, hiddenFlagsVG);

        // Update global
        Flags.setGlobalFlags(currentFlags, this);

        updateMoreIndicator();
    }

    void updateMoreIndicator() {
        if (hiddenFlagsVG.getChildCount() == 0) {
            overflowButton.setImageDrawable(null);
        } else {
            overflowButton.setImageResource(hiddenFlagsVG.getVisibility() == View.VISIBLE ? R.drawable.arrow_down : R.drawable.arrow_right);
        }
    }

    /**
     * Sets up a ViewGroup with the flags received. The state of the flag is read from flagsStatePref.
     * The default state of the flag is read from defaultFlags. Also sets currentFlags.
     *
     * @param flags flags to add
     * @param vg    ViewGroup to fill with flags
     */
    private void fillWithFlags(Set<String> flags, ViewGroup vg) {
        vg.removeAllViews();

        for (String flag : flags) {
            var checkbox_text = Inflater.inflate(R.layout.dialog_flags_entry, vg, getActivity());

            // Checkbox
            var checkBox = checkbox_text.<ImageView>findViewById(R.id.state);
            boolean bool;
            switch (valueOrDefault(flagsStatePref.get(flag), FlagsConfig.FlagState.AUTO)) {
                case ON:
                    bool = true;
                    break;
                case OFF:
                    bool = false;
                    break;
                case AUTO:
                default:
                    bool = defaultFlags.isSet(flag);
            }
            currentFlags.setFlag(flag, bool);

            checkBox.setTag(R.id.text, flag);
            AndroidUtils.toggleableListener(checkBox,
                    v -> {
                        currentFlags.setFlag(flag, !currentFlags.isSet(flag));

                        // Update global
                        Flags.setGlobalFlags(currentFlags, this);

                        // To update debug module view of GlobalData
                        setUrl(new UrlData(getUrl()).dontTriggerOwn().asMinorUpdate());
                    },
                    v -> checkBox.setImageResource(currentFlags.isSet(flag) ? R.drawable.flag_on : R.drawable.flag_off)
            );

            // Text
            ((TextView) checkbox_text.findViewById(R.id.text)).setText(flag);

            // Color indicators
            var defaultIndicator = checkbox_text.findViewById(R.id.defaultIndicator);
            var preferenceIndicator = checkbox_text.findViewById(R.id.preferenceIndicator);

            checkBox.setTag(R.id.defaultIndicator, defaultIndicator);
            checkBox.setTag(R.id.preferenceIndicator, preferenceIndicator);

            setColors(flag, defaultIndicator, preferenceIndicator);
        }

    }

    void setColors(String flag, View defaultIndicator, View preferenceIndicator) {
        AndroidUtils.setRoundedColor(defaultFlags.isSet(flag) ? R.color.good : R.color.bad, defaultIndicator);

        int color;
        switch (valueOrDefault(flagsStatePref.get(flag), FlagsConfig.FlagState.AUTO)) {
            case ON:
                color = R.color.good;
                break;
            case OFF:
                color = R.color.bad;
                break;
            case AUTO:
            default:
                color = R.color.grey;
        }

        AndroidUtils.setRoundedColor(color, preferenceIndicator);
    }

}

class FlagsConfig extends AModuleConfig {

    protected static final String CONF_FILE = "flags_editor_settings";

    public FlagsConfig(ModulesActivity activity) {
        super(activity);
    }

    @Override
    public int getLayoutId() {
        return R.layout.config_flags;
    }

    @Override
    public void onInitialize(View views) {
        views.findViewById(R.id.button).setOnClickListener(showDialog -> {
            View flagsDialogLayout = getActivity().getLayoutInflater().inflate(R.layout.flags_editor, null);
            ViewGroup box = flagsDialogLayout.findViewById(R.id.box);
            InternalFile file = new InternalFile(CONF_FILE, getActivity());

            // Get all flags
            fillBoxViewGroup(box, file, FlagsModule.DEFAULT_GROUP);

            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setView(flagsDialogLayout)
                    .setPositiveButton(views.getContext().getText(R.string.save), (dialog, which) -> {
                        // Save the settings
                        storePreferences(box, file, FlagsModule.DEFAULT_GROUP);
                    })
                    .setNegativeButton(views.getContext().getText(android.R.string.cancel), null)
                    .setNeutralButton(views.getContext().getText(R.string.reset), null)
                    .show();

            alertDialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(listener -> {
                // Reset current group flags (does not save)
                resetFlags(box);
            });

            // Search
            ((EditText) flagsDialogLayout.findViewById(R.id.search)).addTextChangedListener(new DefaultTextWatcher() {
                @Override
                public void afterTextChanged(Editable text) {
                    for (int i = 0; i < box.getChildCount(); i++) {
                        var text_spinner_checkbox = box.getChildAt(i);
                        String flag = ((TextView) text_spinner_checkbox.findViewById(R.id.text)).getText().toString();
                        String search = text.toString();
                        // Set visibility based on search text
                        text_spinner_checkbox.setVisibility(JavaUtils.containsWords(flag, search) ? View.VISIBLE : View.GONE);
                    }
                }
            });

            // TODO add dialog button to set all to on/off/auto
        });

    }

    private void fillBoxViewGroup(ViewGroup vg, InternalFile file, String group) {

        String prefString = file.get();
        JSONObject oldPref = null; // Null if there is no file or fails to parse
        try {
            oldPref = prefString == null ? null : new JSONObject(file.get()).getJSONObject("groups").getJSONObject(group);
        } catch (JSONException ignored) {
        }


        // Fill the box
        for (String flag : Flags.getCompatibleFlags().keySet()) {
            var text_spinner_checkbox = Inflater.inflate(R.layout.flags_editor_entry, vg, getActivity());

            TextView textView = text_spinner_checkbox.findViewById(R.id.text);
            textView.setText(flag);

            var flagState = text_spinner_checkbox.<CycleImageButton<FlagState>>findViewById(R.id.state);
            flagState.setStates(List.of(FlagState.values()));

            // Load preferences from settings
            if (oldPref != null) {
                JSONObject flagPref;
                try {
                    flagPref = oldPref.getJSONObject(flag);

                    // select current option
                    flagState.setCurrentState(valueOrDefault(Enums.toEnum(FlagState.class, flagPref.getInt("state")),
                            FlagState.AUTO));
                    var show = text_spinner_checkbox.<ImageButton>findViewById(R.id.show);
                    show.setTag(flagPref.getBoolean("show"));
                    AndroidUtils.toggleableListener(show,
                            v -> v.setTag(v.getTag() == Boolean.FALSE),
                            v -> v.setImageResource(v.getTag() == Boolean.TRUE ? R.drawable.show : R.drawable.hide)
                    );
                } catch (JSONException ignored) {
                }
            }
        }
    }

    private void storePreferences(ViewGroup vg, InternalFile file, String group) {
        // Retrieve previous config, to keep other groups
        JSONObject oldSettings = null;
        String content = file.get();
        // It's ok if there is no file yet
        if (content != null) {
            try {
                oldSettings = new JSONObject(content);
            } catch (JSONException ignore) {
                // If the json fails to parse then we will create a new file
            }
        }

        try {
            // Collect all the settings of the vg
            JSONObject newSettings = new JSONObject();
            for (int i = 0; i < vg.getChildCount(); i++) {
                View v = vg.getChildAt(i);

                FlagState state = v.<CycleImageButton<FlagState>>findViewById(R.id.state).getCurrentState();
                boolean show = v.findViewById(R.id.show).getTag() == Boolean.TRUE;
                newSettings.put(((TextView) v.findViewById(R.id.text)).getText().toString(),
                        new JSONObject()
                                .put("state", state.getId())
                                .put("show", show));
            }
            // If there are no old settings, create a new one
            // Replace the old settings from group with the new ones
            newSettings = oldSettings == null ?
                    new JSONObject().put("groups", new JSONObject().put(group, newSettings)) :
                    oldSettings.put("groups", oldSettings.getJSONObject("groups").put(group, newSettings));
            // TODO should groups be sorted?
            file.set(newSettings.toString());
        } catch (JSONException e) {
            e.printStackTrace();
            Toast.makeText(getActivity(), R.string.toast_invalid, Toast.LENGTH_SHORT).show();
        }
    }

    private void resetFlags(ViewGroup vg) {
        // Set everything to default values
        for (int i = 0; i < vg.getChildCount(); i++) {
            View v = vg.getChildAt(i);
            v.<CycleImageButton<FlagState>>findViewById(R.id.state).setCurrentState(FlagState.AUTO);
            var visible = v.<ImageButton>findViewById(R.id.show);
            visible.setImageResource(R.drawable.hide);
            visible.setTag(Boolean.FALSE);
        }
    }

    public enum FlagState implements Enums.IdEnum, Enums.ImageEnum {
        AUTO(0, R.drawable.flag_auto),
        ON(1, R.drawable.flag_on),
        OFF(2, R.drawable.flag_off),
        ;

        // -----

        private final int id;
        private final int imageResource;

        FlagState(int id, int imageResource) {
            this.id = id;
            this.imageResource = imageResource;
        }

        @Override
        public int getId() {
            return id;
        }

        @Override
        public int getImageResource() {
            return imageResource;
        }
    }
}