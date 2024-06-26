package sidekick;

/**
 * FilteredTreeModel.java
 *  
 * Modified by Matt Gilbert on 2008/02/26 from the source distribution of LimeWire.
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

/**
 * This class provides a filtered view on an underlying {@link TreeModel}. Nodes
 * may be associated with keywords that can be searched for hiding all nodes
 * that do not match the search term.
 */
public class FilteredTreeModel implements TreeModel
{

	private boolean ignoreCase;

	private FilteredTreeModelListener listener;

	private List<TreeModelListener> listeners = new ArrayList<TreeModelListener>();

	/** The underlying data model. */
	private TreeModel model;

	private ParentProvider parentProvider;

	/** Maps search keywords to lists of matching nodes. */
	private TreeMap<String, List<Object>> searchMap = new TreeMap<String, List<Object>>();

	/**
	 * Currently visible nodes. If <code>null</code>, all nodes are visible.
	 */
	private Set<Object> visibleNodes;

	/**
	 * Constructs a filtering tree model.
	 * 
	 * @param model
	 *                the underlying data model
	 * @param ignoreCase
	 *                if true, filtering is case insensitive
	 */
	public FilteredTreeModel(DefaultTreeModel model, boolean ignoreCase)
	{
		this(model, ignoreCase, new TreeNodeParentProvider());
	}

	/**
	 * Constructs a filtering tree model.
	 * 
	 * @param model
	 *                the underlying data model
	 * @param ignoreCase
	 *                if true, filtering is case insensitive
	 * @param parentProvider
	 *                used to retrieve parents of nodes
	 */
	public FilteredTreeModel(TreeModel model, boolean ignoreCase, ParentProvider parentProvider)
	{
		this.ignoreCase = ignoreCase;

		this.listener = new FilteredTreeModelListener();
		setModel(model, parentProvider);
	}

	/**
	 * Associates <code>node</code> with a search <code>key</code>.
	 */
	public void addSearchKey(Object node, String key)
	{
		key = normalize(key);
		List<Object> value = searchMap.get(key);
		if (value == null)
		{
			value = new ArrayList<Object>(1);
			searchMap.put(key, value);
		}
		value.add(node);
	}

	public void addTreeModelListener(TreeModelListener l)
	{
		listeners.add(l);
	}

	/**
	 * Makes all nodes in the tree visible.
	 */
	public void clearFilter()
	{
		filterByText(null);
	}

	protected SortedMap<String, List<Object>> getMatches(String text)
	{
		SortedMap<String, List<Object>> map = new TreeMap<String, List<Object>>();
		for (String s : searchMap.keySet())
		{
			if (s.indexOf(text) != -1)
				map.put(s, searchMap.get(s));
		}
		return map;
	}

	/**
	 * Hides nodes from the tree that do not match <code>text</code>.
	 * 
	 * @param text
	 *                search text
	 */
	public void filterByText(String text)
	{
		text = normalize(text);
		if (text == null || text.length() == 0)
		{
			visibleNodes = null;
		}
		else
		{
			visibleNodes = new HashSet<Object>();
			String[] keywords = text.split(" ");
			for (int i = 0; i < keywords.length; i++)
			{
				SortedMap<String, List<Object>> nodeListByKey = getMatches(text);
				if (i == 0)
				{
					for (List<Object> nodes : nodeListByKey.values())
					{
						visibleNodes.addAll(nodes);
					}
				}
				else
				{
					Set<Object> allNew = new HashSet<Object>();
					for (List<Object> nodes : nodeListByKey.values())
					{
						allNew.addAll(nodes);
					}
					visibleNodes.retainAll(allNew);
				}
			}
			ensureParentsVisible();
		}

		TreeModelEvent event = new TreeModelEvent(this, new Object[] { model.getRoot() });
		for (TreeModelListener listener : listeners)
		{
			listener.treeStructureChanged(event);
		}
	}

	public Object getChild(Object parent, int index)
	{
		if (visibleNodes == null)
		{
			return model.getChild(parent, index);
		}

		int visibleIndex = 0;
		for (int i = 0, count = model.getChildCount(parent); i < count; i++)
		{
			Object node = model.getChild(parent, i);
			if (visibleNodes.contains(node) && index == visibleIndex++)
			{
				return node;
			}
		}
		throw new ArrayIndexOutOfBoundsException();
	}

	public int getChildCount(Object parent)
	{
		if (visibleNodes == null)
		{
			return model.getChildCount(parent);
		}

		int visibleCount = 0;
		for (int i = 0, count = model.getChildCount(parent); i < count; i++)
		{
			if (visibleNodes.contains(model.getChild(parent, i)))
			{
				visibleCount++;
			}
		}
		return visibleCount;
	}

	public int getIndexOfChild(Object parent, Object child)
	{
		if (visibleNodes == null)
		{
			return model.getIndexOfChild(parent, child);
		}

		int visibleIndex = 0;
		for (int i = 0, count = model.getChildCount(parent); i < count; i++)
		{
			Object node = model.getChild(parent, i);
			if (visibleNodes.contains(node))
			{
				if (node == child)
				{
					return visibleIndex;
				}
				visibleIndex++;
			}
		}
		return -1;
	}

	/**
	 * Returns the underlying data model.
	 */
	public TreeModel getModel()
	{
		return model;
	}

	public Object getRoot()
	{
		return model.getRoot();
	}

	public boolean isLeaf(Object node)
	{
		return model.isLeaf(node);
	}

	public boolean isVisible(Object node)
	{
		return visibleNodes == null || visibleNodes.contains(node);
	}

	private String normalize(String text)
	{
		if (text != null)
		{
			if (ignoreCase)
			{
				text = text.toLowerCase();
			}
		}
		return text;
	}

	public void reload()
	{
		TreeModelEvent event = new TreeModelEvent(this, new Object[] { model.getRoot() });
		for (TreeModelListener listener : listeners)
		{
			listener.treeStructureChanged(event);
		}
	}

	public void removeSearchKey(Object node, String key)
	{
		key = normalize(key);
		List<Object> value = searchMap.get(key);
		if (value != null)
		{
			value.remove(node);
			if (value.isEmpty())
			{
				searchMap.remove(key);
			}
		}
	}

	public void removeTreeModelListener(TreeModelListener l)
	{
		listeners.remove(l);
	}

	/**
	 * Sets the underlying data model.
	 * 
	 * @param model
	 *                data model
	 */
	public void setModel(DefaultTreeModel model)
	{
		setModel(model, new TreeNodeParentProvider());
	}

	/**
	 * Sets the underlying data model.
	 * 
	 * @param model
	 *                data model
	 * @param parentProvider
	 *                used to retrieve parents of nodes
	 */
	public void setModel(TreeModel model, ParentProvider parentProvider)
	{
		if (model == null || parentProvider == null)
		{
			throw new IllegalArgumentException();
		}

		if (this.model != null)
		{
			this.model.removeTreeModelListener(listener);
		}

		this.model = model;
		this.parentProvider = parentProvider;
		this.model.addTreeModelListener(listener);
		searchMap.clear();

		reset();
	}

	/**
	 * Sets all nodes visible.
	 */
	public void reset()
	{
		this.visibleNodes = null;
		reload();
	}

	/**
	 * Sets all parents of the visible nodes visible.
	 */
	private void ensureParentsVisible()
	{
		Set<Object> parentNodes = new HashSet<Object>();
		for (Object node : visibleNodes)
		{
			Object parentNode = parentProvider.getParent(node);
			while (parentNode != null)
			{
				parentNodes.add(parentNode);
				parentNode = parentProvider.getParent(parentNode);
			}
		}
		visibleNodes.addAll(parentNodes);
	}

	public void valueForPathChanged(TreePath path, Object newValue)
	{
		model.valueForPathChanged(path, newValue);
	}

	/**
	 * Forwards events from the underlying data model to listeners.
	 */
	private class FilteredTreeModelListener implements TreeModelListener
	{

		public TreeModelEvent refactorEvent(TreeModelEvent event)
		{
			if (visibleNodes != null)
			{
				List<Object> children = new ArrayList<Object>(
					event.getChildren().length);
				List<Integer> indicieList = new ArrayList<Integer>(event
					.getChildIndices().length);

				for (Object node : event.getChildren())
				{
					visibleNodes.add(node);
				}

				Object parent = event.getTreePath().getLastPathComponent();
				for (Object node : event.getChildren())
				{
					children.add(node);
					indicieList.add(getIndexOfChild(parent, node));
				}

				int[] indicies = new int[indicieList.size()];
				for (int i = 0; i < indicies.length; i++)
				{
					indicies[i] = indicieList.get(i);
				}
				event = new TreeModelEvent(event.getSource(), event.getTreePath(),
					indicies, children.toArray(new Object[0]));
			}
			return event;
		}

		public void treeNodesChanged(TreeModelEvent event)
		{
			if (!isVisible(event.getTreePath().getLastPathComponent()))
			{
				return;
			}

			event = refactorEvent(event);
			for (TreeModelListener listener : listeners)
			{
				listener.treeNodesChanged(event);
			}
		}

		public void treeNodesInserted(TreeModelEvent event)
		{
			if (!isVisible(event.getTreePath().getLastPathComponent()))
			{
				return;
			}

			event = refactorEvent(event);
			for (TreeModelListener listener : listeners)
			{
				listener.treeNodesInserted(event);
			}
		}

		public void treeNodesRemoved(TreeModelEvent event)
		{
			if (!isVisible(event.getTreePath().getLastPathComponent()))
			{
				return;
			}

			for (TreeModelListener listener : listeners)
			{
				listener.treeStructureChanged(event);
			}
		}

		public void treeStructureChanged(TreeModelEvent event)
		{
			if (!isVisible(event.getTreePath().getLastPathComponent()))
			{
				return;
			}

			for (TreeModelListener listener : listeners)
			{
				listener.treeStructureChanged(event);
			}
		}

	}

	/**
	 * Implements <code>TreeNodeParentProvider</code> for tree models that
	 * use {@link TreeNode} objects such as {@link DefaultTreeModel}.
	 */
	public static class TreeNodeParentProvider implements ParentProvider
	{

		public Object getParent(Object node)
		{
			return ((TreeNode) node).getParent();
		}

	}

	/** Interface to retrieve parent nodes. */
	public interface ParentProvider
	{

		/**
		 * Returns the parent of <code>node</code>.
		 * 
		 * @return null, if <code>node</code> does not have a parent;
		 *         the parent, otherwise
		 */
		Object getParent(Object node);

	}

}
