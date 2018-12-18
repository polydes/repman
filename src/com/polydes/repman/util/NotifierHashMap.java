package com.polydes.repman.util;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.log4j.Logger;

public class NotifierHashMap<K,V> extends HashMap<K,V>
{
	private static final Logger log = Logger.getLogger(NotifierHashMap.class);
	
	public interface Listener<V>
	{
		void mapChanged(HashMapEvent<V> event);
	}
	
	public enum EventType
	{
		VALUE_ADDED,
		VALUE_REMOVED,
		MAP_CHANGED
	}
	
	public static class HashMapEvent<T>
	{
		public EventType type;
		public T value;
		
		@Override
		public String toString()
		{
			return "HashMapEvent [type=" + type + ", value=" + value + "]";
		}
	}
	
	private final ArrayList<Listener<V>> listeners = new ArrayList<Listener<V>>();
	
	private boolean quiet;
	
	public void addListener(Listener<V> l)
	{
		listeners.add(l);
	}
	
	public void removeListener(Listener<V> l)
	{
		listeners.remove(l);
	}
	
	@Override
	public V put(K key, V value)
	{
		V prev = super.put(key, value);
		
		if(!quiet && !listeners.isEmpty())
		{
			if(prev != null)
			{
				HashMapEvent<V> event = new HashMapEvent<V>();
				event.type = EventType.VALUE_REMOVED;
				event.value = prev;
				log.debug(event);
				
				for(Listener<V> l : listeners)
					l.mapChanged(event);
			}
			
			if(value != null)
			{
				HashMapEvent<V> event = new HashMapEvent<V>();
				event.type = EventType.VALUE_ADDED;
				event.value = value;
				log.debug(event);
				
				for(Listener<V> l : listeners)
					l.mapChanged(event);
			}
		}
		
		return prev;
	}
	
	@Override
	public V remove(Object key)
	{
		V prev = super.remove(key);
		
		if(!quiet && prev != null && !listeners.isEmpty())
		{
			HashMapEvent<V> event = new HashMapEvent<V>();
			event.type = EventType.VALUE_REMOVED;
			event.value = prev;
			log.debug(event);
			
			for(Listener<V> l : listeners)
				l.mapChanged(event);
		}
		
		return prev;
	}
	
	@Override
	public void clear()
	{
		super.clear();
		
		if(!quiet && !listeners.isEmpty())
		{
			HashMapEvent<V> event = new HashMapEvent<V>();
			event.type = EventType.MAP_CHANGED;
			event.value = null;
			log.debug(event);
			
			for(Listener<V> l : listeners)
				l.mapChanged(event);
		}
	}
	
	public void beginChanging()
	{
		quiet = true;
	}
	
	public void finishChanging()
	{
		quiet = false;
		if(!listeners.isEmpty())
		{
			HashMapEvent<V> event = new HashMapEvent<V>();
			event.type = EventType.MAP_CHANGED;
			event.value = null;
			log.debug(event);
			
			for(Listener<V> l : listeners)
				l.mapChanged(event);
		}
	}
}
