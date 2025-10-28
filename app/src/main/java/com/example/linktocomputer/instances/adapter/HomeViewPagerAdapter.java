package com.example.linktocomputer.instances.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.example.linktocomputer.fragment.HomeFragment;
import com.example.linktocomputer.fragment.SettingFragment;
import com.example.linktocomputer.fragment.TransmitFragment;

public class HomeViewPagerAdapter extends FragmentStateAdapter {
    private static final Fragment[] mFragments = new Fragment[]{new HomeFragment(),new TransmitFragment(), new SettingFragment()};

    public HomeViewPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }


    @NonNull
    @Override
    public Fragment createFragment(int position) {
        return mFragments[position];
    }

    @Override
    public int getItemCount() {
        return mFragments.length;
    }

    public HomeFragment getHomeFragment(){
        return (HomeFragment) mFragments[0];
    }
    public TransmitFragment getTransmitFragment(){
        return (TransmitFragment) mFragments[1];
    }
}
