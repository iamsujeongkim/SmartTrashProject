package com.example.smarttrashproject2

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class GridAdapter (
    private val context: Context,
    private val items: Array<String>,
    private val images: IntArray
): BaseAdapter(){

    private val inflater: LayoutInflater = LayoutInflater.from(context)

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.grid_item, parent, false)

        val icon: ImageView = view.findViewById(R.id.imageIcon)
        val label: TextView = view.findViewById(R.id.textLabel)

        icon.setImageResource(images[position])
        label.text = items[position]

        return view
    }
}