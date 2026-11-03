package com.waterlivewallpaper.features.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.waterlivewallpaper.MainActivity
import com.waterlivewallpaper.R

class ComingSoonFragment : Fragment() {

    companion object {
        private const val ARG_TITLE = "arg_title"
        private const val ARG_STICKER = "arg_sticker"
        private const val ARG_DESC = "arg_desc"

        fun newInstance(title: String, stickerResId: Int, desc: String): ComingSoonFragment {
            val fragment = ComingSoonFragment()
            val args = Bundle().apply {
                putString(ARG_TITLE, title)
                putInt(ARG_STICKER, stickerResId)
                putString(ARG_DESC, desc)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_coming_soon, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = activity as? MainActivity
        val title = arguments?.getString(ARG_TITLE) ?: "Module"
        val stickerRes = arguments?.getInt(ARG_STICKER) ?: R.drawable.sticker_water_drop_3d
        val desc = arguments?.getString(ARG_DESC) ?: "This personal module is in active development."

        view.findViewById<View>(R.id.btnComingSoonBack).setOnClickListener {
            mainActivity?.onBackPressedDispatcher?.onBackPressed()
        }

        view.findViewById<TextView>(R.id.textComingSoonHeaderTitle).text = title
        view.findViewById<ImageView>(R.id.imgComingSoonSticker).setImageResource(stickerRes)
        view.findViewById<TextView>(R.id.textComingSoonTitle).text = title
        view.findViewById<TextView>(R.id.textComingSoonDesc).text = desc

        view.findViewById<View>(R.id.btnComingSoonNotify).setOnClickListener {
            mainActivity?.showCustomToast(
                "Notification Scheduled",
                "You'll be notified when $title launches in the next release!",
                stickerRes
            )
        }

        view.findViewById<View>(R.id.btnComingSoonHome).setOnClickListener {
            mainActivity?.navigateToHome()
        }
    }
}
