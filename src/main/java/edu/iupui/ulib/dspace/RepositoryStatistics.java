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

import org.apache.log4j.Logger;
import org.dspace.content.MetadataField;
import org.dspace.content.MetadataSchema;

import org.dspace.core.Constants;
import org.dspace.core.Context;
import org.dspace.storage.rdbms.*;

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
public class RepositoryStatistics extends HttpServlet
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
        TableRow row;

        // Response header
        resp.setContentType("text/xml; encoding='UTF-8'");
        resp.setStatus(HttpServletResponse.SC_OK);

        // Response body
        PrintWriter responseWriter = resp.getWriter();
        responseWriter.print("<?xml version='1.0' encoding='UTF-8'?>");

        responseWriter.print("<dspace-repository-statistics date='");
        log.debug("Ready to write date");
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
        df.setTimeZone(utcZone);
        SimpleDateFormat tf = new SimpleDateFormat("HHmmss");
        tf.setTimeZone(utcZone);
        Date now = new Date();
        responseWriter.print(df.format(now));
        responseWriter.print('T');
        responseWriter.print(tf.format(now));
        responseWriter.print("Z'>");
        log.debug("Wrote the date");

        try
        {
            dsContext = new Context();

            if (DC_TITLE_FIELD < 0)
                DC_TITLE_FIELD = MetadataField.findByElement(dsContext,
                        MetadataSchema.DC_SCHEMA_ID, "title", null).getFieldID();
            
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(community_id) AS communities FROM community;");
            if (null != row)
                responseWriter.printf(
                        " <statistic name='communities'>%d</statistic>",
                        row.getLongColumn("communities"));
            
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(collection_id) AS collections FROM collection;");
            if (null != row)
                responseWriter.printf(
                        " <statistic name='collections'>%d</statistic>",
                        row.getLongColumn("collections"));
            
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(item_id) AS items FROM item WHERE NOT withdrawn;"); // TODO Oracle-ize
            if (null != row)
                responseWriter.printf(
                        " <statistic name='items'>%d</statistic>",
                        row.getLongColumn("items"));

            log.debug("Counting, summing bitstreams");
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(bitstream_id) AS bitstreams," +
                            " sum(size_bytes) AS totalBytes" +
                            " FROM bitstream bs" +
                    		"  JOIN bundle2bitstream USING(bitstream_id)" +
                    		"  JOIN bundle USING(bundle_id)" +
                    		"  JOIN item2bundle USING(bundle_id)" +
                    		"  JOIN item USING(item_id)" +
                            "  JOIN metadatavalue md ON (" +
                            "   md.resource_id = bs.bitstream_id" +
                            "   AND md.resource_type_id = ?" +
                            "   AND md.metadata_field_id = ?)" +
                    		" WHERE NOT withdrawn" + // TODO Oracle-ize
                    		"  AND NOT deleted" + // TODO Oracle-ize
                            "  AND md.text_value = 'ORIGINAL'" +
                            ";", Constants.BUNDLE, DC_TITLE_FIELD);
            if (null != row)
            {
                log.debug("Writing count");
                responseWriter.printf(" <statistic name='bitstreams'>%d</statistic>",
                        row.getLongColumn("bitstreams"));
                log.debug("Writing total size");
                responseWriter.printf(" <statistic name='totalBytes'>%d</statistic>",
                        row.getNumericColumn("totalBytes").toBigInteger());
                log.debug("Completed writing count, size");
            }
            
            log.debug("Counting, summing image bitstreams");
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(bitstream_id) AS images," +
                    " sum(size_bytes) AS imageBytes" +
                    " FROM bitstream bs" +
                    "  JOIN bitstreamformatregistry USING(bitstream_format_id)" +
                    "  JOIN bundle2bitstream USING(bitstream_id)" +
                    "  JOIN bundle USING(bundle_id)" +
                    "  JOIN item2bundle USING(bundle_id)" +
                    "  JOIN item USING(item_id)" +
                    "  JOIN metadatavalue md ON (" +
                    "   md.resource_id = bs.bitstream_id" +
                    "   AND md.resource_type_id = ?" +
                    "   AND md.metadata_field_id = ?)" +
                    " WHERE mimetype LIKE 'image/%'" +
                    "  AND NOT deleted" + // TODO Oracle-ize
                    "  AND NOT withdrawn" + // TODO Oracle-ize
                    ";", Constants.BUNDLE, DC_TITLE_FIELD
                    );
            if (null != row)
            {
                responseWriter.printf(" <statistic name='images'>%d</statistic>",
                        row.getLongColumn("images"));
                responseWriter.printf(" <statistic name='imageBytes'>%d</statistic>",
                        row.getNumericColumn("imageBytes").toBigInteger());
            }

            /* TODO workflow items
            row = DatabaseManager.querySingle(dsContext,
                    "SELECT count(community_id) AS communities FROM community");
            if (null != row)
                responseWriter.printf(" <statistic name='communities'>%d</statistic>",
                        row.getLongColumn("column_id"));
             */
        }
        catch (SQLException e)
        {
            log.debug("caught SQLException");
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
        }

        responseWriter.print("</dspace-repository-statistics>");
        log.debug("Finished report");
    }

    /** HttpServlet implements Serializable for some strange reason */
    private static final long serialVersionUID = -98582768658080267L;
}
