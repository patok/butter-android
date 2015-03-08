/*
 * This file is part of Popcorn Time.
 *
 * Popcorn Time is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Popcorn Time is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Popcorn Time. If not, see <http://www.gnu.org/licenses/>.
 */

package pct.droid.dialogfragments;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.devspark.robototextview.widget.RobotoTextView;
import com.squareup.picasso.Picasso;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import pct.droid.R;
import pct.droid.activities.MediaDetailActivity;
import pct.droid.base.preferences.Prefs;
import pct.droid.base.providers.media.models.Episode;
import pct.droid.base.providers.media.models.Media;
import pct.droid.base.providers.media.models.Show;
import pct.droid.base.providers.meta.MetaProvider;
import pct.droid.base.providers.subs.SubsProvider;
import pct.droid.base.utils.LocaleUtils;
import pct.droid.base.utils.PixelUtils;
import pct.droid.base.utils.PrefUtils;
import pct.droid.base.utils.StringUtils;
import pct.droid.base.utils.ThreadUtils;
import pct.droid.base.utils.VersionUtils;
import pct.droid.fragments.StreamLoadingFragment;
import pct.droid.widget.OptionSelector;

public class EpisodeDialogFragment extends DialogFragment {

    public static final String EXTRA_EPISODE = "episode";
    public static final String EXTRA_SHOW = "show";
    public static final String EXTRA_COLOR = "palette";
    private MetaProvider mMetaProvider;
    private SubsProvider mSubsProvider;
    private boolean mAttached = false;
    private String mSelectedSubtitleLanguage, mSelectedQuality;
    private Episode mEpisode;
    private Show mShow;
    private int mPaletteColor;

    @InjectView(R.id.play_button)
    ImageButton mPlayButton;
    @InjectView(R.id.header_image)
    ImageView mHeaderImage;
    @InjectView(R.id.title)
    TextView mTitle;
    @InjectView(R.id.aired)
    TextView mAired;
    @InjectView(R.id.synopsis)
    RobotoTextView mSynopsis;
    @InjectView(R.id.read_more)
    Button mReadMore;
    @InjectView(R.id.subtitles)
    OptionSelector mSubtitles;
    @InjectView(R.id.quality)
    OptionSelector mQuality;

    public static EpisodeDialogFragment newInstance(Show show, Episode episode, int color) {
        EpisodeDialogFragment frag = new EpisodeDialogFragment();
        Bundle args = new Bundle();
        args.putParcelable(EXTRA_SHOW, show);
        args.putParcelable(EXTRA_EPISODE, episode);
        args.putInt(EXTRA_COLOR, color);
        frag.setArguments(args);
        return frag;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = LayoutInflater.from(new ContextThemeWrapper(getActivity(), R.style.Theme_PopcornTime)).inflate(R.layout
                .fragment_dialog_episode, container, false);
        ButterKnife.inject(this, v);

        if(VersionUtils.isJellyBean()) {
            mPlayButton.setBackgroundDrawable(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, mPaletteColor));
        } else {
            mPlayButton.setBackground(PixelUtils.changeDrawableColor(mPlayButton.getContext(), R.drawable.play_button_circle, mPaletteColor));
        }

        return v;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        int width = PixelUtils.getScreenWidth(getActivity());
        int height = PixelUtils.getScreenHeight(getActivity());
        dialog.getWindow().setLayout(width, height);
        dialog.getWindow().setWindowAnimations(R.style.Theme_PopcornTime_DialogFade);
        return dialog;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, R.style.Theme_Dialog_Transparent);

        mPaletteColor = getArguments().getInt(EXTRA_COLOR);
        mShow = getArguments().getParcelable(EXTRA_SHOW);
        mEpisode = getArguments().getParcelable(EXTRA_EPISODE);
        mMetaProvider = mEpisode.getMetaProvider();
        mSubsProvider = mEpisode.getSubsProvider();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (null != mMetaProvider) mMetaProvider.cancel();
        if (null != mSubsProvider) mSubsProvider.cancel();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mTitle.setText(mEpisode.title);
        Date airedDate = new Date(mEpisode.aired * 1000);
        mAired.setText(String.format(getString(R.string.aired), new SimpleDateFormat("MMM dd, yyyy", new Locale(LocaleUtils.getCurrent())).format(airedDate)));

        mSynopsis.setText(mEpisode.overview);
        mSynopsis.post(new Runnable() {
            @Override
            public void run() {
                boolean ellipsized = false;
                Layout layout = mSynopsis.getLayout();
                if(layout == null) return;
                int lines = layout.getLineCount();
                if(lines > 0) {
                    int ellipsisCount = layout.getEllipsisCount(lines-1);
                    if (ellipsisCount > 0) {
                        ellipsized = true;
                    }
                }
                mReadMore.setVisibility(ellipsized ? View.VISIBLE : View.GONE);
            }
        });

        mSubtitles.setFragmentManager(getFragmentManager());
        mQuality.setFragmentManager(getFragmentManager());
        mSubtitles.setTitle(R.string.subtitles);
        mQuality.setTitle(R.string.quality);

        final String[] qualities = mEpisode.torrents.keySet().toArray(new String[mEpisode.torrents.size()]);
        Arrays.sort(qualities);
        mQuality.setData(qualities);
        mSelectedQuality = qualities[qualities.length - 1];
        mQuality.setText(mSelectedQuality);
        mQuality.setDefault(qualities.length - 1);

        mQuality.setListener(new OptionSelector.SelectorListener() {
            @Override
            public void onSelectionChanged(int position, String value) {
                mSelectedQuality = value;
            }
        });

        mSubtitles.setText(R.string.loading_subs);
        mSubtitles.setClickable(false);
        mSubsProvider.getList(mShow, mEpisode, new SubsProvider.Callback() {
            @Override
            public void onSuccess(Map<String, String> subtitles) {
                if(!mAttached) return;

                String[] languages = subtitles.keySet().toArray(new String[subtitles.size()]);
                Arrays.sort(languages);
                final String[] adapterLanguages = new String[languages.length + 1];
                adapterLanguages[0] = "no-subs";
                System.arraycopy(languages, 0, adapterLanguages, 1, languages.length);

                String[] readableNames = new String[adapterLanguages.length];
                for (int i = 0; i < readableNames.length; i++) {
                    String language = adapterLanguages[i];
                    if (language.equals("no-subs")) {
                        readableNames[i] = getString(R.string.no_subs);
                    } else {
                        Locale locale = LocaleUtils.toLocale(language);
                        readableNames[i] = locale.getDisplayName(locale);
                    }
                }

                mSubtitles.setListener(new OptionSelector.SelectorListener() {
                    @Override
                    public void onSelectionChanged(int position, String value) {
                        onSubtitleLanguageSelected(adapterLanguages[position]);
                    }
                });
                mSubtitles.setData(readableNames);
                ThreadUtils.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSubtitles.setClickable(true);
                    }
                });

                String defaultSubtitle = PrefUtils.get(mSubtitles.getContext(), Prefs.SUBTITLE_DEFAULT, null);
                if (subtitles.containsKey(defaultSubtitle)) {
                    onSubtitleLanguageSelected(defaultSubtitle);
                    mSubtitles.setDefault(Arrays.asList(adapterLanguages).indexOf(defaultSubtitle));
                } else {
                    onSubtitleLanguageSelected("no-subs");
                    mSubtitles.setDefault(Arrays.asList(adapterLanguages).indexOf("no-subs"));
                }
            }

            @Override
            public void onFailure(Exception e) {
                mSubtitles.setData(new String[0]);
                mSubtitles.setClickable(true);
            }
        });

        mMetaProvider.getEpisodeMeta(mEpisode.imdbId, mEpisode.season, mEpisode.episode, new MetaProvider.Callback() {
            @Override
            public void onResult(MetaProvider.MetaData metaData, Exception e) {
                String imageUrl = mEpisode.headerImage;
                if (e == null) {
                    imageUrl = metaData.images.poster;
                }
                Picasso.with(mHeaderImage.getContext()).load(imageUrl).into(mHeaderImage);
            }
        });
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAttached = true;
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mAttached = false;
    }

    @OnClick(R.id.play_button)
    public void play() {
        String quality = mEpisode.torrents.keySet().toArray(new String[1])[0];
        Media.Torrent torrent = mEpisode.torrents.get(quality);
        StreamLoadingFragment.StreamInfo streamInfo = new StreamLoadingFragment.StreamInfo(mEpisode, mShow, torrent.url, null, quality);
        ((MediaDetailActivity) getActivity()).playStream(streamInfo);
    }

    @OnClick(R.id.read_more)
    public void openReadMore(View v) {
        if (getFragmentManager().findFragmentByTag("overlay_fragment") != null)
            return;
        SynopsisDialogFragment synopsisDialogFragment = new SynopsisDialogFragment();
        Bundle b = new Bundle();
        b.putString("text", mEpisode.overview);
        synopsisDialogFragment.setArguments(b);
        synopsisDialogFragment.show(getFragmentManager(), "overlay_fragment");
    }

    private void onSubtitleLanguageSelected(String language) {
        mSelectedSubtitleLanguage = language;
        if (!language.equals("no-subs")) {
            final Locale locale = LocaleUtils.toLocale(language);
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSubtitles.setText(StringUtils.uppercaseFirst(locale.getDisplayName(locale)));
                }
            });
        } else {
            ThreadUtils.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mSubtitles.setText(R.string.no_subs);
                }
            });
        }
    }

}
