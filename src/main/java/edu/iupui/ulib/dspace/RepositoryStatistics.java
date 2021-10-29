/**
 * <!DOCTYPE HTML PUBLIC "-//IETF//DTD HTML 2.0//EN">
 * <html><head>
 * <title>301 Moved Permanently</title>
 * </head><body>
 * <h1>Moved Permanently</h1>
 * <p>The document has moved <a href="https://svn.duraspace.org/dspace/licenses/LICENSE_HEADER">here</a>.</p>
 * </body></html>
 */
package edu.iupui.ulib.dspace;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;

import org.apache.log4j.Logger;
import org.dspace.content.MetadataSchema;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.content.service.MetadataFieldService;
import org.dspace.core.Context;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Expose some simple measures of the repository's size as an XML document via a
 * web service.
 *
 * <p><em>NOTE WELL:</em>  we go straight to the database for much of this
 * information.  This could break if there are significant changes in the
 * schema.  The object model doesn't provide these statistics, though.</p>
 * 
 * @author Mark H. Wood
 */
public class RepositoryStatistics
        extends HttpServlet
{
	private static final TimeZone utcZone = TimeZone.getTimeZone("UTC");

    protected static final Logger log
        = Logger.getLogger(RepositoryStatistics.class);

    private static int DC_TITLE_FIELD = -1;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException
    {
        log.debug("Entering RepositoryStatistics.doGet");
        Context dsContext;
        DocumentBuilder documentBuilder;
        Transformer transformer;

        try {
            documentBuilder = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder();
            transformer = TransformerFactory.newInstance()
                    .newTransformer();
        } catch (ParserConfigurationException
                | TransformerFactoryConfigurationError
                | TransformerConfigurationException ex) {
            throw new ServletException("Cannot build response document", ex);
        }

        // Response header
        resp.setContentType("text/xml; encoding='UTF-8'");
        resp.setStatus(HttpServletResponse.SC_OK);

        // Response body
        Document document = documentBuilder.newDocument();

        Element root = document.createElement("dspace-repository-statistics");
        document.appendChild(root);

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
        root.setAttribute("date", format.format(new Date()));

        try
        {
            dsContext = new Context();
            ContentServiceFactory contentServiceFactory
                    = ContentServiceFactory.getInstance();
            Element element;

            MetadataFieldService metadataFieldService
                    = contentServiceFactory.getMetadataFieldService();
            if (DC_TITLE_FIELD < 0)
                DC_TITLE_FIELD = metadataFieldService.findByElement(dsContext,
                        MetadataSchema.DC_SCHEMA, "title", null).getID();

            element = document.createElement("statistic");
            element.setAttribute("name", "communities");
            element.setTextContent(String.valueOf(contentServiceFactory.getCommunityService().countTotal(dsContext)));
            root.appendChild(element);

            element = document.createElement("statistic");
            element.setAttribute("name", "collections");
            element.setTextContent(String.valueOf(contentServiceFactory.getCollectionService().countTotal(dsContext)));
            root.appendChild(element);

            ItemService itemService = contentServiceFactory.getItemService();
            element = document.createElement("statistic");
            element.setAttribute("name", "items");
            element.setTextContent(String.valueOf(itemService.countTotal(dsContext)
                            - itemService.countWithdrawnItems(dsContext)));

            BitstreamCounts row;

            log.debug("Counting, summing bitstreams");
            row = (BitstreamCounts) new StatisticsDAOImpl().doHQLQuery(dsContext,
                    "SELECT new edu.iupui.ulib.dspace.BitstreamCounts(count(*), sum(bs.sizeBytes))" +
                            " FROM Bundle bnd" +
                            "  JOIN bnd.items i" +
                            "  JOIN bnd.bitstreams bs" +
                            "  JOIN bnd.metadata md" +
                            "  JOIN md.metadataField mf" +
                            "   WITH mf.element = 'title'" +
                            " WHERE i.withdrawn is false" +
                            "  AND bs.deleted is false" +
                            "  AND md.value = 'ORIGINAL'"
            );
            if (null != row)
            {
                log.debug("Writing count");
                Object count = row;
                log.debug("bitstream count is " + count.toString());
                element = document.createElement("statistic");
                element.setAttribute("name", "bitstreams");
                element.setTextContent(String.valueOf(row.getCount()));
                root.appendChild(element);

                log.debug("Writing total size");
                Object size = row;
                log.debug("bitstream size is " + size.toString());
                element = document.createElement("statistic");
                element.setAttribute("name", "totalBytes");
                element.setTextContent(String.valueOf(row.getTotalSize()));
                root.appendChild(element);
                log.debug("Completed writing count, size");
            }
            
            log.debug("Counting, summing image bitstreams");
            row = (BitstreamCounts) new StatisticsDAOImpl().doHQLQuery(dsContext,
                    "SELECT new edu.iupui.ulib.dspace.BitstreamCounts(count(*), sum(bs.sizeBytes))" +
                    " FROM Bundle bnd" +
                    "  JOIN bnd.items i" +
                    "  JOIN bnd.bitstreams bs" +
                    "  JOIN bs.bitstreamFormat bsf" +
                    " WHERE i.withdrawn IS false" +
                    "  AND bs.deleted IS false" +
                    "  AND bsf.mimetype LIKE 'image/%'"
                    );
            if (null != row)
            {
                element = document.createElement("statistic");
                element.setAttribute("name", "images");
                element.setTextContent(String.valueOf(row.getCount()));
                root.appendChild(element);

                element = document.createElement("statistic");
                element.setAttribute("name", "imageBytes");
                element.setTextContent(String.valueOf(row.getTotalSize()));
                root.appendChild(element);
            }

            /* TODO workflow items
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(community_id) AS communities FROM community");
            if (null != row)
            {
                element = document.createElement("statistic");
                element.setAttribute("name", "communities");
                element.setTextContent(String.valueOf(SOMETHING);
                root.appendChild(element);
            }
             */
        } catch (SQLException e) {
            log.debug("caught SQLException");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
        }

        PrintWriter responseWriter = resp.getWriter();
        try {
            transformer.transform(new DOMSource(document),
                    new javax.xml.transform.stream.StreamResult(responseWriter));
        } catch (TransformerException ex) {
            throw new ServletException("Cannot format or send document.", ex);
        }
        log.debug("Finished report");
    }

    /** HttpServlet implements Serializable for some strange reason */
    public static final long SerialVersionUID = 060200l;
}
