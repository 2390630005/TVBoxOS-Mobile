package com.github.tvbox.osc.ui.activity;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import com.blankj.utilcode.util.GsonUtils;
import com.blankj.utilcode.util.SPUtils;
import com.blankj.utilcode.util.ToastUtils;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseVbActivity;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.VideoInfo;
import com.github.tvbox.osc.constant.CacheConst;
import com.github.tvbox.osc.databinding.ActivityLocalPlayBinding;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.IjkMediaPlayer;
import com.github.tvbox.osc.player.MyVideoView;
import com.github.tvbox.osc.player.TrackInfo;
import com.github.tvbox.osc.player.TrackInfoBean;
import com.github.tvbox.osc.player.controller.LocalVideoController;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LOG;
import com.github.tvbox.osc.util.PlayerHelper;
import com.google.common.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;

public class LocalPlayActivity extends BaseVbActivity<ActivityLocalPlayBinding> {


    private MyVideoView mVideoView;
    LocalVideoController mController;
    JSONObject mVodPlayerCfg;
    private List<VideoInfo> mVideoList = new ArrayList<>();
    private int mPosition;

    @Override
    protected void init() {
        mVideoView = mBinding.player;
        mVideoView.startFullScreen();
        Bundle bundle = getIntent().getExtras();
        String videoListJson =  bundle.getString("videoList");
        mVideoList = GsonUtils.fromJson(videoListJson, new TypeToken<List<VideoInfo>>(){}.getType());
        mPosition = bundle.getInt("position", 0);

        initController();
        initPlayerCfg();
        mVideoView.setVideoController(mController); //设置控制器
        play(false);

        new Handler()
                .postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (mVideoView.getCurrentPlayState() == VideoView.STATE_PREPARED){//不知道为啥部分长视频(不确定是不是因为时长/大小)会卡在准备完成状态,所以延迟重置下状态
                            mVideoView.pause();
                            mVideoView.resume();
                        }
                    }
                },500);
    }

    /**
     * 跳转到上/下一集,需重新播放
     */
    private void play(boolean fromSkip) {
        VideoInfo videoInfo = mVideoList.get(mPosition);

        String path = videoInfo.getPath();

        String uri = "";
        File file = new File(path);
        if(file.exists()){
            uri = Uri.parse("file://"+file.getAbsolutePath()).toString();
        }
        mController.setTitle(videoInfo.getDisplayName());
        mVideoView.setUrl(uri); //设置视频地址

        mVideoView.setProgressManager(new ProgressManager() {
            @Override
            public void saveProgress(String url, long progress) {// 就本地视频页面用sp,其余用Hawk
                //有点本地文件确实总时长,设置下总时长,为什么用path,因为电影列表要通过媒体文件的path获取缓存的时长/进度,存取报纸缓存的key一直
                SPUtils.getInstance(CacheConst.VIDEO_DURATION_SP).put(path, mVideoView.getDuration());
                SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).put(path, progress);
            }

            @Override
            public long getSavedProgress(String url) {
                return SPUtils.getInstance(CacheConst.VIDEO_PROGRESS_SP).getLong(path);
            }
        });

        PlayerHelper.updateCfg(mVideoView, mVodPlayerCfg);

        if (fromSkip){
            mVideoView.replay(false);
        }else {
            mVideoView.start(); //开始播放，不调用则不自动播放
        }
    }

    private void initController() {
        mController = new LocalVideoController(this);
        mController.setListener(new LocalVideoController.VodControlListener() {
            @Override
            public void playNext(boolean rmProgress) {
//                String preProgressKey = progressKey;
//                LocalPlayActivity.this.playNext(rmProgress);
                if (mPosition == mVideoList.size() - 1){
                    ToastUtils.showShort("当前已经是最后一集了");
                } else {
                    mPosition++;
                    play(true);
                }
            }

            @Override
            public void playPre() {
                //playPrevious();
                if (mPosition == 0){
                    ToastUtils.showShort("当前已经是第一集了");
                }else {
                    mPosition--;
                    play(true);
                }
            }

            @Override
            public void changeParse(ParseBean pb) {

            }

            @Override
            public void updatePlayerCfg() {

            }

            @Override
            public void replay(boolean replay) {

            }

            @Override
            public void errReplay() {

            }

            @Override
            public void selectSubtitle() {

            }

            @Override
            public void selectAudioTrack() {

            }

            @Override
            public void prepared() {

            }

            @Override
            public void toggleFullScreen() {
                finish();
            }

            @Override
            public void exit() {
                finish();
            }
        });

    }

    void initPlayerCfg() {
        try {
            mVodPlayerCfg = new JSONObject();
        } catch (Throwable th) {
            mVodPlayerCfg = new JSONObject();
        }
        try {
            if (!mVodPlayerCfg.has("pl")) {
                mVodPlayerCfg.put("pl", Hawk.get(HawkConfig.PLAY_TYPE, 1));
            }
            if (!mVodPlayerCfg.has("pr")) {
                mVodPlayerCfg.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, 0));
            }
            if (!mVodPlayerCfg.has("ijk")) {
                mVodPlayerCfg.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            }
            if (!mVodPlayerCfg.has("sc")) {
                mVodPlayerCfg.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, 0));
            }
            if (!mVodPlayerCfg.has("sp")) {
                mVodPlayerCfg.put("sp", 1.0f);
            }
            if (!mVodPlayerCfg.has("st")) {
                mVodPlayerCfg.put("st", 0);
            }
            if (!mVodPlayerCfg.has("et")) {
                mVodPlayerCfg.put("et", 0);
            }
        } catch (Throwable th) {

        }
        mController.setPlayerConfig(mVodPlayerCfg);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoView.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
    }


    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        super.finish();
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, ""));
    }
}