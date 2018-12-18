package com.polydes.repman.util.io;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class XMLHelper
{
	private static final Logger log = Logger.getLogger(XMLHelper.class);
	
	private static final ThreadLocal<DocumentBuilder> xmlBuilder = new ThreadLocal<DocumentBuilder>()
	{
		@Override
		protected DocumentBuilder initialValue()
		{
			try
			{
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				factory.setNamespaceAware(true);
				DocumentBuilder builder = factory.newDocumentBuilder();
				
				builder.setEntityResolver(new EntityResolver()
				{
					@Override
					public InputSource resolveEntity(String publicID, String systemID) throws SAXException, IOException
					{
						return new InputSource(new ByteArrayInputStream(new byte[0]));
					}
				});
				
				return builder;
			}
			catch (ParserConfigurationException ex)
			{
				log.error(ex);
				return null;
			}
		}
	};
	
	private static final ThreadLocal<Transformer> xmlTransformer = new ThreadLocal<Transformer>()
	{
		@Override
		protected Transformer initialValue()
		{
			try
			{
				TransformerFactory factory = TransformerFactory.newInstance();
				Transformer transformer = factory.newTransformer();
				transformer.setOutputProperty(OutputKeys.INDENT, "yes");
				transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
				return transformer;
			}
			catch (TransformerConfigurationException ex)
			{
				log.error(ex);
				return null;
			}
		}
	};
	
	private static void transformXML(Source xmlSource, Result outputTarget) throws TransformerException
	{
		Transformer t = xmlTransformer.get();
		t.transform(xmlSource, outputTarget);
		t.reset();
		t.setOutputProperty(OutputKeys.INDENT, "yes");
		t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
	}
	
	public static boolean isValidXML(File f) throws IOException
	{
		try
		{
			xmlBuilder.get().parse(f);
			xmlBuilder.get().reset();

			return true;
		}

		catch (SAXException e)
		{
			return false;
		}
	}

	public static Document readXMLFromString(String s) throws IOException
	{
		return readXMLFromStream(new ByteArrayInputStream(s.getBytes("UTF-8")));
	}

	public static Document readXMLFromFile(File file) throws IOException
	{
		try
		{
			Document doc = xmlBuilder.get().parse(file);
			xmlBuilder.get().reset();

			return doc;
		}

		catch (SAXException e)
		{
			throw new IOException("SAXException in file: " + file.getName(), e);
		}
	}

	public static Document readXMLFromFile(String uri) throws IOException
	{
		try
		{
			Document doc = xmlBuilder.get().parse(uri);
			xmlBuilder.get().reset();
			
			return doc;
		}

		catch (SAXException e)
		{
			throw new IOException("SAXException in file: " + uri, e);
		}
	}

	public static Document readXMLFromStream(InputStream is) throws IOException
	{
		try
		{
			Document doc = xmlBuilder.get().parse(is);
			xmlBuilder.get().reset();
			
			return doc;
		}

		catch (SAXException e)
		{
			throw new IOException("SAXException in InputStream", e);
		}
	}

	public static Document newDocument()
	{
		Document doc = xmlBuilder.get().newDocument();
		xmlBuilder.get().reset();
		
		return doc;
	}

	public static String writeXMLToString(Document doc) throws IOException
	{
		ByteArrayOutputStream os = null;

		try
		{
			os = new ByteArrayOutputStream();

			writeXMLToStream(doc, os);

			String toReturn = os.toString("UTF-8");

			os.close();

			return toReturn;
		}

		finally
		{
			IOUtils.closeQuietly(os);
		}
	}

	public static void writeXMLToFile(Document doc, File filename) throws IOException
	{
		try (FileOutputStream os = new FileOutputStream(filename))
		{
			writeXMLToStream(doc, os);
		}
	}

	private static void writeXMLToStream(Document doc, OutputStream os) throws IOException
	{
		OutputStreamWriter osw = null;

		try
		{
			osw = new OutputStreamWriter(os, "UTF-8");
			Result result = new StreamResult(osw);
			DOMSource source = new DOMSource(doc);

			transformXML(source, result);
			
			osw.close();
		}

		catch (TransformerException e)
		{
			throw new IOException(e);
		}

		finally
		{
			IOUtils.closeQuietly(osw);
		}
	}
}
