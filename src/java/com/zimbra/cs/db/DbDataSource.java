/*
 * ***** BEGIN LICENSE BLOCK *****
 * 
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2008 Zimbra, Inc.
 * 
 * The contents of this file are subject to the Yahoo! Public License
 * Version 1.0 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * 
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.account.DataSource;
import com.zimbra.cs.db.DbPool.Connection;
import com.zimbra.cs.mailbox.Mailbox;
import com.zimbra.cs.mailbox.Metadata;


public class DbDataSource {

	public static class DataSourceItem {
		public int itemId;
		public String remoteId;
		public Metadata md;
		public DataSourceItem(int i, String r, Metadata m) { itemId = i; remoteId = r; md = m; }
	}
	
    public static final String TABLE_DATA_SOURCE_ITEM = "data_source_item";

    public static void addMapping(Mailbox mbox, DataSource ds, DataSourceItem item) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        String dataSourceId = ds.getId();

        if (item.remoteId == null)
        	item.remoteId = "";
        
        ZimbraLog.datasource.debug("Adding mapping for dataSource %s: itemId(%d), remoteId(%s)", ds.getName(), item.itemId, item.remoteId);
        
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("INSERT INTO ");
            sb.append(getTableName(mbox));
            sb.append(" (");
            sb.append(DbMailItem.MAILBOX_ID);
            sb.append("data_source_id, item_id, remote_id, metadata) VALUES (");
            sb.append(DbMailItem.MAILBOX_ID_VALUE);
            sb.append("?, ?, ?, ?)");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, dataSourceId);
            stmt.setInt(i++, item.itemId);
            stmt.setString(i++, item.remoteId);
            stmt.setString(i++, DbMailItem.checkMetadataLength((item.md == null) ? null : item.md.toString()));
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            if (Db.supports(Db.Capability.ON_DUPLICATE_KEY) && Db.errorMatches(e, Db.Error.DUPLICATE_ROW)) {
                DbPool.closeStatement(stmt);
                DbPool.quietClose(conn);
            	updateMapping(mbox, ds, item);
            } else {
                throw ServiceException.FAILURE("Unable to add mapping for dataSource "+ds.getName(), e);
            }
        } finally {
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    }

    public static void updateMapping(Mailbox mbox, DataSource ds, DataSourceItem item) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ZimbraLog.datasource.debug("Updating mapping for dataSource %s: itemId(%d), remoteId(%s)", ds.getName(), item.itemId, item.remoteId);
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("UPDATE ");
            sb.append(getTableName(mbox));
            sb.append(" SET remote_id = ?, metadata = ? WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append(" item_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setString(i++, item.remoteId);
            stmt.setString(i++, DbMailItem.checkMetadataLength((item.md == null) ? null : item.md.toString()));
            stmt.setInt(i++, mbox.getId());
            stmt.setInt(i++, item.itemId);
            stmt.executeUpdate();
            conn.commit();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to update mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    }

    public static void deleteMappings(Mailbox mbox, DataSource ds, Collection<Integer> itemIds) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ZimbraLog.datasource.debug("Deleting %d mappings for dataSource %s", itemIds.size(), ds.getName());
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ");
            sb.append(getTableName(mbox));
            sb.append(" WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append(" data_source_id = ? AND item_id IN ");
            sb.append(DbUtil.suitableNumberOfVariables(itemIds));
            stmt = conn.prepareStatement(sb.toString());
            
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            for (int itemId : itemIds)
            	stmt.setInt(i++, itemId);

            int numRows = stmt.executeUpdate();
            conn.commit();
            ZimbraLog.datasource.debug("Deleted %d mappings for %s", numRows, ds.getName());
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to delete mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    }

    public static void deleteAllMappings(Mailbox mbox, DataSource ds) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ZimbraLog.datasource.debug("Deleting all mappings for dataSource %s", ds.getName());
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("DELETE FROM ");
            sb.append(getTableName(mbox));
            sb.append(" WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append(" data_source_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            int numRows = stmt.executeUpdate();
            conn.commit();
            ZimbraLog.datasource.debug("Deleted %d mappings for %s", numRows, ds.getName());
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to delete mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    }

    public static boolean hasMapping(Mailbox mbox, DataSource ds, int itemId) throws ServiceException {
    	DataSourceItem item = getMapping(mbox, ds, itemId);
    	return item.remoteId != null;
    }
    
    public static Collection<DataSourceItem> getAllMappings(Mailbox mbox, DataSource ds) throws ServiceException {
    	ArrayList<DataSourceItem> items = new ArrayList<DataSourceItem>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        ZimbraLog.datasource.debug("Get all mappings for %s", ds.getName());
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT item_id, remote_id, metadata FROM ");
            sb.append(getTableName(mbox));
            sb.append(" WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append("  data_source_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            rs = stmt.executeQuery();
            while (rs.next()) {
            	Metadata md = null;
            	String buf = rs.getString(3);
            	if (buf != null)
            		md = new Metadata(buf);
            	items.add(new DataSourceItem(rs.getInt(1), rs.getString(2), md));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to get mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    	
    	return items;
    }
    
    public static Collection<DataSourceItem> getAllMappingsInFolder(Mailbox mbox, DataSource ds, int folderId) throws ServiceException {
    	ArrayList<DataSourceItem> items = new ArrayList<DataSourceItem>();
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        ZimbraLog.datasource.debug("Get all mappings for %s in folder %d", ds.getName(), folderId);
        try {
        	String thisTable = getTableName(mbox);
        	String mailItemTable = DbMailbox.getDatabaseName(mbox) + ".mail_item";
        	String IN_THIS_MAILBOX_AND = thisTable+".mailbox_id = ? AND ";
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT item_id, remote_id, ").append(thisTable).append(".metadata FROM ");
            sb.append(thisTable);
            sb.append(" INNER JOIN ");
            sb.append(mailItemTable);
            sb.append(" ON  ").append(thisTable).append(".item_id = ").append(mailItemTable).append(".id");
            sb.append(" WHERE ");
            sb.append(IN_THIS_MAILBOX_AND);
            sb.append("  data_source_id = ? AND folder_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            stmt.setInt(i++, folderId);
            rs = stmt.executeQuery();
            while (rs.next()) {
            	Metadata md = null;
            	String buf = rs.getString(3);
            	if (buf != null)
            		md = new Metadata(buf);
            	items.add(new DataSourceItem(rs.getInt(1), rs.getString(2), md));
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to get mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    	
    	return items;
    }
    
    public static DataSourceItem getMapping(Mailbox mbox, DataSource ds, int itemId) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        String remoteId = null;
    	Metadata md = null;
    	
        ZimbraLog.datasource.debug("Get mapping for %s, itemId=%d", ds.getName(), itemId);
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT remote_id, metadata FROM ");
            sb.append(getTableName(mbox));
            sb.append(" WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append("  data_source_id = ? AND item_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            stmt.setInt(i++, itemId);
            rs = stmt.executeQuery();
            if (rs.next()) {
            	remoteId = rs.getString(1);
            	String buf = rs.getString(2);
            	if (buf != null)
            		md = new Metadata(buf);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to get mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    	
    	return new DataSourceItem(itemId, remoteId, md);
    }

    public static DataSourceItem getReverseMapping(Mailbox mbox, DataSource ds, String remoteId) throws ServiceException {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        
        int itemId = 0;
        Metadata md = null;
    	
        ZimbraLog.datasource.debug("Get reverse mapping for %s, remoteId=%s", ds.getName(), remoteId);
        try {
            conn = DbPool.getConnection();
            StringBuilder sb = new StringBuilder();
            sb.append("SELECT item_id, metadata FROM ");
            sb.append(getTableName(mbox));
            sb.append(" WHERE ");
            sb.append(DbMailItem.IN_THIS_MAILBOX_AND);
            sb.append("  data_source_id = ? AND remote_id = ?");
            stmt = conn.prepareStatement(sb.toString());
            int i = 1;
            stmt.setInt(i++, mbox.getId());
            stmt.setString(i++, ds.getId());
            stmt.setString(i++, remoteId);
            rs = stmt.executeQuery();
            if (rs.next()) {
            	itemId = rs.getInt(1);
            	String buf = rs.getString(2);
            	if (buf != null)
            		md = new Metadata(buf);
            }
            rs.close();
            stmt.close();
        } catch (SQLException e) {
            throw ServiceException.FAILURE("Unable to get reverse mapping for dataSource "+ds.getName(), e);
        } finally {
            DbPool.closeResults(rs);
            DbPool.closeStatement(stmt);
            DbPool.quietClose(conn);
        }
    	
    	return new DataSourceItem(itemId, remoteId, md);
    }

    public static String getTableName(Mailbox mbox) {
        return DbMailbox.getDatabaseName(mbox) + "." + TABLE_DATA_SOURCE_ITEM;
    }
}
