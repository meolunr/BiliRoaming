package me.iacn.biliroaming

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView


/**
 * Created by iAcn on 2019/7/14
 * Email i@iacn.me
 */
class ColorChooseDialog(context: Context, defColor: Int) : AlertDialog.Builder(context) {
    private lateinit var sampleView: View
    private lateinit var etColor: EditText

    private lateinit var sbColorR: SeekBar
    private lateinit var sbColorG: SeekBar
    private lateinit var sbColorB: SeekBar

    private lateinit var tvColorR: TextView
    private lateinit var tvColorG: TextView
    private lateinit var tvColorB: TextView

    init {
        val moduleContext = context.createPackageContext(BuildConfig.APPLICATION_ID, Context.CONTEXT_IGNORE_SECURITY)
        val contentView = View.inflate(moduleContext, R.layout.dialog_color_choose, null)
        setView(contentView)
        findView(contentView)
        setEditTextListener()
        setSeekBarListener()

        updateState(defColor)
        etColor.setText(String.format("%06X", 0xFFFFFF and defColor))

        setTitle("自选颜色")
        setNegativeButton("取消", null)
    }

    private fun findView(view: View) {
        sampleView = view.findViewById(R.id.view_sample)
        etColor = view.findViewById(R.id.et_color)

        sbColorR = view.findViewById(R.id.sb_colorR)
        sbColorG = view.findViewById(R.id.sb_colorG)
        sbColorB = view.findViewById(R.id.sb_colorB)

        tvColorR = view.findViewById(R.id.tv_colorR)
        tvColorG = view.findViewById(R.id.tv_colorG)
        tvColorB = view.findViewById(R.id.tv_colorB)
    }

    private fun setEditTextListener() {
        etColor.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                try {
                    updateState(Color.parseColor("#${s.toString()}"))
                } catch (ignore: IllegalArgumentException) {
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setSeekBarListener() {
        val listener: OnSeekBarChangeListener = object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val color = Color.rgb(sbColorR.progress, sbColorG.progress, sbColorB.progress)
                    etColor.setText(String.format("%06X", 0xFFFFFF and color))
                }
                tvColorR.text = sbColorR.progress.toString()
                tvColorG.text = sbColorG.progress.toString()
                tvColorB.text = sbColorB.progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        }

        sbColorR.setOnSeekBarChangeListener(listener)
        sbColorG.setOnSeekBarChangeListener(listener)
        sbColorB.setOnSeekBarChangeListener(listener)
    }

    private fun updateState(color: Int) {
        sampleView.setBackgroundColor(color)

        val progressR = Color.red(color)
        val progressG = Color.green(color)
        val progressB = Color.blue(color)

        sbColorR.progress = progressR
        sbColorG.progress = progressG
        sbColorB.progress = progressB

        tvColorR.text = progressR.toString()
        tvColorG.text = progressG.toString()
        tvColorB.text = progressB.toString()

        updateSeekBarColor(color)
    }

    private fun updateSeekBarColor(color: Int) {
        val colorList = ColorStateList(arrayOf(intArrayOf()), intArrayOf(color))

        sbColorR.progressTintList = colorList
        sbColorR.thumbTintList = colorList

        sbColorG.progressTintList = colorList
        sbColorG.thumbTintList = colorList

        sbColorB.progressTintList = colorList
        sbColorB.thumbTintList = colorList
    }

    fun getColor(): Int = Color.rgb(sbColorR.progress, sbColorG.progress, sbColorB.progress)
}