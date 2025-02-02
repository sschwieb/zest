/*******************************************************************************
* Copyright (c) 2011 Stephan Schwiebert. All rights reserved. This program and
* the accompanying materials are made available under the terms of the Eclipse
* Public License v1.0 which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
* <p/>
* Contributors: Stephan Schwiebert - initial API and implementation
*******************************************************************************/
package org.eclipse.zest.cloudio.util;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Stack;

import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;

/**
 * A two-dimensional tree structure to store non-overlapping
 * rectangles.
 * @author sschwieb
 *
 */
public class RectTree {
	
	private final int minResolution;
	
	private short xOffset, yOffset;
	
	private final RectNode root;
	
	private LinkedList<RectNode> leaves;
	
	private static short EMPTY = -1, MISC = 0;
	
	public Rectangle minBounds = new Rectangle(Short.MAX_VALUE, Short.MAX_VALUE, Short.MIN_VALUE, Short.MIN_VALUE);
	
	private Stack<RectNode> lastPath = new Stack<RectNode>();
	
	class RectNode {

		final SmallRect rect;
		
		private RectNode[] children;
		
		private final SmallRect[] childAreas;

		short filled = EMPTY;

		public RectNode(SmallRect rect) {
			this.rect = rect;
			final int width = rect.width/2;
			final int height = rect.height/2;
			if(rect.width > minResolution) {
				this.childAreas = new SmallRect[4];
				// top left
				childAreas[0] = new SmallRect(rect.x, rect.y, width, height);
				// top right
				childAreas[1] = new SmallRect(rect.x+width, rect.y, width, height);
				// bottom left
				childAreas[2] = new SmallRect(rect.x, rect.y+height, width, height);
				// bottom right
				childAreas[3] = new SmallRect(rect.x+width, rect.y+height, width, height);
			} else {
				this.childAreas = null;
			}
		}
		
		private int getChildIndex(SmallRect r) {
			int index = 0;
			if(r.y>= childAreas[3].y) {
				if(r.x >= childAreas[3].x) {
					index = 3;
				} else {
					index = 2;
				}
			} else {
				if(r.x >= childAreas[1].x) {
					index = 1;
				} 
			}
			return index;
		}

		public void insert(SmallRect r, short id) {
			if(rect.width == minResolution) {
				filled = id;
				return;
			}
			int i = getChildIndex(r);
			lastPath.push(this);
			if(children == null) {
				children = new RectNode[4];
			}
			if(children[i] == null) {
				children[i] = new RectNode(childAreas[i]);
			}
			if(children[i].rect.width >= minResolution && children[i].rect.height >= minResolution) {
				children[i].insert(r, id);
				if(children[i].rect.width == minResolution) {
					children[i].filled = id;
					SmallRect c = children[i].rect;
					if(c.x < minBounds.x) minBounds.x = c.x;
					if(c.y < minBounds.y) minBounds.y = c.y;
					if(c.x > minBounds.width) minBounds.width = c.x;
					if(c.y > minBounds.height) minBounds.height = c.y;
				}
			}
			boolean filled = true;
			if(children != null) {
				for(int ix = 0; ix < 4; ix++) {
					if(children[ix] == null) {
						filled = false; 
						break;
					}
					if(children[ix].filled == EMPTY) {
						filled = false;
						break;
					}
				}			
			}
			if(filled) {
				this.filled = MISC;
			}
		}
		
		public boolean isAvailable(final SmallRect oRect) {
			if(filled >= MISC) return false;
			if(children == null) {
				return filled == EMPTY;
			}
			final int i = getChildIndex(oRect);
			if(children[i] == null) return true;
			return children[i].isAvailable(oRect);
		}


		public short getWordId(Point position) {
			if(filled > 0) return filled;
			if(children == null) {
				return filled;
			}
			for(int i = 0; i < childAreas.length; i++) {
				if(childAreas[i].intersects(position.x-2, position.y-2, 4, 4) && children[i] != null) {
					return children[i].getWordId(position);
				}
			}
			return 0;
		}

	}
	
	public RectTree(SmallRect root, int minResolution) {
		this.minResolution = minResolution;
		this.root = new RectNode(root);
	}
			
	public void insert(SmallRect r, short id) {
		while(!lastPath.isEmpty()) {
			RectNode node = lastPath.pop();
			if(node.rect.intersects(r)) {
				node.insert(r, id);
				return;
			}
		}
		root.insert(r, id);
	}

	public void move(int x, int y) {
		this.xOffset = (short) x;
		this.yOffset = (short) y;
	}

	public boolean fits(final short[][] mainTree) {
		LinkedList<RectNode> leaves = getLeaves();
		Iterator<RectNode> nodes = leaves.iterator();
		while(nodes.hasNext()) {
			RectNode node = nodes.next();
			if(mainTree[(node.rect.x+xOffset)/minResolution][(node.rect.y+yOffset)/minResolution] != EMPTY) {
				nodes.remove();
				leaves.addFirst(node);
				return false;
			}
		}
		return true;
	}

	LinkedList<RectNode> getLeaves() {
		if(leaves == null) {
			leaves = new LinkedList<RectNode>();
			addLeaves(leaves, root);
		}
		return leaves;
	}

	private void addLeaves(List<RectNode> leaves, RectNode current) {
		if(current.children == null)  {
			if(current.filled != EMPTY) {
			leaves.add(current);
			}
		} else {
			for(int i = 0; i < 4; i++) {
				if(current.children[i] == null) continue;
				addLeaves(leaves, current.children[i]);
			}
		}
	}
	
	public void place(final short[][] mainTree, short id) {
		Collection<RectNode> leaves = getLeaves();
		for (RectNode node : leaves) {
			mainTree[(node.rect.x+xOffset)/minResolution][(node.rect.y+yOffset)/minResolution] = id;
		}
	}

	public void releaseRects() {
		getLeaves();
		root.children = null;
	}



}
