package com.polydes.repman.util.io;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//https://odoepner.wordpress.com/2012/07/13/turn-org-w3c-dom-nodelist-into-iterable/
public class IterableNodeList
{
	/**
	 * @param n
	 *            An XML node list
	 * @return A newly created Iterable for the given node list, allowing
	 *         iteration over the nodes in a for-each loop. The iteration
	 *         behavior is undefined if concurrent modification of the node list
	 *         occurs.
	 */
	public static Iterable<Node> nodes(final NodeList n)
	{
		return new Iterable<Node>()
		{
			@Override
			public Iterator<Node> iterator()
			{
				return new Iterator<Node>()
				{
					int index = 0;

					@Override
					public boolean hasNext()
					{
						return index < n.getLength();
					}

					@Override
					public Node next()
					{
						if (hasNext())
						{
							return n.item(index++);
						}
						else
						{
							throw new NoSuchElementException();
						}
					}

					@Override
					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
	
	/**
	 * @param n
	 *            An XML node list
	 * @return A newly created Iterable for the given node list, allowing
	 *         iteration over the nodes in a for-each loop. The iteration
	 *         behavior is undefined if concurrent modification of the node list
	 *         occurs.
	 */
	public static Iterable<Element> elements(final NodeList n)
	{
		return new Iterable<Element>()
		{
			@Override
			public Iterator<Element> iterator()
			{
				return new Iterator<Element>()
				{
					int index = 0;

					@Override
					public boolean hasNext()
					{
						return index < n.getLength();
					}

					@Override
					public Element next()
					{
						if (hasNext())
						{
							return (Element) n.item(index++);
						}
						else
						{
							throw new NoSuchElementException();
						}
					}

					@Override
					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				};
			}
		};
	}
}
