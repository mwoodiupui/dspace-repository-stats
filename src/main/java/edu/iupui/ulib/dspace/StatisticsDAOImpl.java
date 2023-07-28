/*
 * Copyright 2018 Indiana University.  All rights reserved.
 *
 * Mark H. Wood, IUPUI University Library, Apr 13, 2018
 */

package edu.iupui.ulib.dspace;

import java.sql.SQLException;
import javax.persistence.Query;

import org.dspace.content.Bitstream;
import org.dspace.core.AbstractHibernateDSODAO;
import org.dspace.core.Context;

/**
 * Do arbitrary HQL queries for content statistics.
 * @author Mark H. Wood <mwood@iupui.edu>
 */
public class StatisticsDAOImpl
        extends AbstractHibernateDSODAO<Bitstream> {
    /**
     * Execute {@code hql} and return its first result.
     * @param ctx DSpace context.
     * @param hql An HQL query.
     * @return first result of executing {@code hql}.
     * @throws SQLException passed through.
     */
    Object doHQLQuery(Context ctx, String hql)
            throws SQLException {
        Query query = createQuery(ctx, hql);
        return query.getResultList().get(0);
    }
}
