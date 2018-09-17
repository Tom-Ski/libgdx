/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package com.badlogic.gdx.scenes.scene2d.ui;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.utils.ArraySelection;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.Cullable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.UIUtils;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectSet;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

/** A list (aka list box) displays textual items and highlights the currently selected item.
 * <p>
 * {@link ChangeEvent} is fired when the list selection changes.
 * <p>
 * The preferred size of the list is determined by the text bounds of the items and the size of the {@link ListStyle#selection}.
 * @author mzechner
 * @author Nathan Sweet */
public class List<T> extends Widget implements Cullable {
	ListStyle style;
	final Array<T> items = new Array();
	final ArraySelection<T> selection = new ArraySelection(items);
	private Rectangle cullingArea;
	private float prefWidth, prefHeight;
	float itemHeight;
	private int alignment = Align.left;
	int touchDown = -1, overIndex = -1;

	public List (Skin skin) {
		this(skin.get(ListStyle.class));
	}

	public List (Skin skin, String styleName) {
		this(skin.get(styleName, ListStyle.class));
	}

	public List (ListStyle style) {
		selection.setActor(this);
		selection.setRequired(true);

		setStyle(style);
		setSize(getPrefWidth(), getPrefHeight());

		addListener(new InputListener() {
			public boolean keyDown (InputEvent event, int keycode) {
				if (keycode == Keys.A && UIUtils.ctrl() && selection.getMultiple()) {
					selection.clear();
					selection.addAll(items);
					return true;
				}
				return false;
			}

			public boolean touchDown (InputEvent event, float x, float y, int pointer, int button) {
				if (pointer != 0 || button != 0) return true;
				if (selection.isDisabled()) return true;
				getStage().setKeyboardFocus(List.this);
				if (items.size == 0) return true;
				int index = getItemIndexAt(y);
				if (index == -1) return true;
				selection.choose(items.get(index));
				touchDown = index;
				return true;
			}

			public void touchUp (InputEvent event, float x, float y, int pointer, int button) {
				if (pointer != 0 || button != 0) return;
				touchDown = -1;
			}

			public void touchDragged (InputEvent event, float x, float y, int pointer) {
				overIndex = getItemIndexAt(y);
			}

			public boolean mouseMoved (InputEvent event, float x, float y) {
				overIndex = getItemIndexAt(y);
				return false;
			}

			public void exit (InputEvent event, float x, float y, int pointer, Actor toActor) {
				if (pointer == 0) touchDown = -1;
				if (pointer == -1) overIndex = -1;
			}
		});
	}

	public void setStyle (ListStyle style) {
		if (style == null) throw new IllegalArgumentException("style cannot be null.");
		this.style = style;
		invalidateHierarchy();
	}

	/** Returns the list's style. Modifying the returned style may not have an effect until {@link #setStyle(ListStyle)} is
	 * called. */
	public ListStyle getStyle () {
		return style;
	}

	public void layout () {
		BitmapFont font = style.font;
		Drawable selectedDrawable = style.selection;

		itemHeight = font.getCapHeight() - font.getDescent() * 2;
		itemHeight += selectedDrawable.getTopHeight() + selectedDrawable.getBottomHeight();

		prefWidth = 0;
		Pool<GlyphLayout> layoutPool = Pools.get(GlyphLayout.class);
		GlyphLayout layout = layoutPool.obtain();
		for (int i = 0; i < items.size; i++) {
			layout.setText(font, toString(items.get(i)));
			prefWidth = Math.max(layout.width, prefWidth);
		}
		layoutPool.free(layout);
		prefWidth += selectedDrawable.getLeftWidth() + selectedDrawable.getRightWidth();
		prefHeight = items.size * itemHeight;

		Drawable background = style.background;
		if (background != null) {
			prefWidth += background.getLeftWidth() + background.getRightWidth();
			prefHeight += background.getTopHeight() + background.getBottomHeight();
		}
	}

	@Override
	public void draw (Batch batch, float parentAlpha) {
		validate();

		BitmapFont font = style.font;
		Drawable selectedDrawable = style.selection;
		Color fontColorSelected = style.fontColorSelected;
		Color fontColorUnselected = style.fontColorUnselected;

		Color color = getColor();
		batch.setColor(color.r, color.g, color.b, color.a * parentAlpha);

		float x = getX(), y = getY(), width = getWidth(), height = getHeight();
		float itemY = height;

		Drawable background = style.background;
		if (background != null) {
			background.draw(batch, x, y, width, height);
			float leftWidth = background.getLeftWidth();
			x += leftWidth;
			itemY -= background.getTopHeight();
			width -= leftWidth + background.getRightWidth();
		}

		float textOffsetX = selectedDrawable.getLeftWidth(), textWidth = width - textOffsetX - selectedDrawable.getRightWidth();
		float textOffsetY = selectedDrawable.getTopHeight() - font.getDescent();

		font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b, fontColorUnselected.a * parentAlpha);
		for (int i = 0; i < items.size; i++) {
			if (cullingArea == null || (itemY - itemHeight <= cullingArea.y + cullingArea.height && itemY >= cullingArea.y)) {
				T item = items.get(i);
				boolean selected = selection.contains(item);
				if (selected) {
					Drawable drawable = selectedDrawable;
					if (touchDown == i && style.down != null) drawable = style.down;
					drawable.draw(batch, x, y + itemY - itemHeight, width, itemHeight);
					font.setColor(fontColorSelected.r, fontColorSelected.g, fontColorSelected.b, fontColorSelected.a * parentAlpha);
				} else if (overIndex == i && style.over != null) //
					style.over.draw(batch, x, y + itemY - itemHeight, width, itemHeight);
				drawItem(batch, font, i, item, x + textOffsetX, y + itemY - textOffsetY, textWidth);
				if (selected) {
					font.setColor(fontColorUnselected.r, fontColorUnselected.g, fontColorUnselected.b,
						fontColorUnselected.a * parentAlpha);
				}
			} else if (itemY < cullingArea.y) {
				break;
			}
			itemY -= itemHeight;
		}
	}

	protected GlyphLayout drawItem (Batch batch, BitmapFont font, int index, T item, float x, float y, float width) {
		String string = toString(item);
		return font.draw(batch, string, x, y, 0, string.length(), width, alignment, false, "...");
	}

	public ArraySelection<T> getSelection () {
		return selection;
	}

	/** Returns the first selected item, or null. */
	public T getSelected () {
		return selection.first();
	}

	/** Sets the selection to only the passed item, if it is a possible choice.
	 * @param item May be null. */
	public void setSelected (T item) {
		if (items.contains(item, false))
			selection.set(item);
		else if (selection.getRequired() && items.size > 0)
			selection.set(items.first());
		else
			selection.clear();
	}

	/** @return The index of the first selected item. The top item has an index of 0. Nothing selected has an index of -1. */
	public int getSelectedIndex () {
		ObjectSet<T> selected = selection.items();
		return selected.size == 0 ? -1 : items.indexOf(selected.first(), false);
	}

	/** Sets the selection to only the selected index.
	 * @param index -1 to clear the selection. */
	public void setSelectedIndex (int index) {
		if (index < -1 || index >= items.size)
			throw new IllegalArgumentException("index must be >= -1 and < " + items.size + ": " + index);
		if (index == -1) {
			selection.clear();
		} else {
			selection.set(items.get(index));
		}
	}

	/** @return null if not over an item. */
	public T getItemAt (float y) {
		int index = getItemIndexAt(y);
		if (index == -1) return null;
		return items.get(index);
	}

	/** @return -1 if not over an item. */
	public int getItemIndexAt (float y) {
		float height = getHeight();
		Drawable background = List.this.style.background;
		if (background != null) {
			height -= background.getTopHeight() + background.getBottomHeight();
			y -= background.getBottomHeight();
		}
		int index = (int)((height - y) / itemHeight);
		if (index < 0 || index >= items.size) return -1;
		return index;
	}

	public void setItems (T... newItems) {
		if (newItems == null) throw new IllegalArgumentException("newItems cannot be null.");
		float oldPrefWidth = getPrefWidth(), oldPrefHeight = getPrefHeight();

		items.clear();
		items.addAll(newItems);
		selection.validate();

		invalidate();
		if (oldPrefWidth != getPrefWidth() || oldPrefHeight != getPrefHeight()) invalidateHierarchy();
	}

	/** Sets the items visible in the list, clearing the selection if it is no longer valid. If a selection is
	 * {@link ArraySelection#getRequired()}, the first item is selected. This can safely be called with a (modified) array returned
	 * from {@link #getItems()}. */
	public void setItems (Array newItems) {
		if (newItems == null) throw new IllegalArgumentException("newItems cannot be null.");
		float oldPrefWidth = getPrefWidth(), oldPrefHeight = getPrefHeight();

		if (newItems != items) {
			items.clear();
			items.addAll(newItems);
		}
		selection.validate();

		invalidate();
		if (oldPrefWidth != getPrefWidth() || oldPrefHeight != getPrefHeight()) invalidateHierarchy();
	}

	public void clearItems () {
		if (items.size == 0) return;
		items.clear();
		selection.clear();
		invalidateHierarchy();
	}

	/** Returns the internal items array. If modified, {@link #setItems(Array)} must be called to reflect the changes. */
	public Array<T> getItems () {
		return items;
	}

	public float getItemHeight () {
		return itemHeight;
	}

	public float getPrefWidth () {
		validate();
		return prefWidth;
	}

	public float getPrefHeight () {
		validate();
		return prefHeight;
	}

	protected String toString (T object) {
		return object.toString();
	}

	public void setCullingArea (Rectangle cullingArea) {
		this.cullingArea = cullingArea;
	}

	/** Sets the horizontal alignment of the list items.
	 * @param alignment See {@link Align}. */
	public void setAlignment (int alignment) {
		this.alignment = alignment;
	}

	/** The style for a list, see {@link List}.
	 * @author mzechner
	 * @author Nathan Sweet */
	static public class ListStyle {
		public BitmapFont font;
		public Color fontColorSelected = new Color(1, 1, 1, 1);
		public Color fontColorUnselected = new Color(1, 1, 1, 1);
		public Drawable selection;
		/** Optional. */
		public Drawable down, over, background;

		public ListStyle () {
		}

		public ListStyle (BitmapFont font, Color fontColorSelected, Color fontColorUnselected, Drawable selection) {
			this.font = font;
			this.fontColorSelected.set(fontColorSelected);
			this.fontColorUnselected.set(fontColorUnselected);
			this.selection = selection;
		}

		public ListStyle (ListStyle style) {
			this.font = style.font;
			this.fontColorSelected.set(style.fontColorSelected);
			this.fontColorUnselected.set(style.fontColorUnselected);
			this.selection = style.selection;
			this.down = style.down;
		}
	}
}
