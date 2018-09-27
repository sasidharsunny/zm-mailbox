/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2018 Synacor, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */
package com.zimbra.cs.mailbox;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;

import com.zimbra.common.localconfig.LC;
import com.zimbra.common.util.ThreadPool;
import com.zimbra.common.util.ZimbraLog;
import com.zimbra.cs.mailbox.redis.RedissonRetryClient;

public final class RedissonClientHolder {

    private final RedissonRetryClient client;
    private static class InstanceHolder {
        public static RedissonClientHolder instance = new RedissonClientHolder();
    }

    private RedissonClientHolder() {
        String uri = null;
        try {
            uri = LC.redis_service_uri.value();
            ZimbraLog.system.info("redis_service_uri=%s", uri);
            Config config = new Config();
            ThreadPool pool = ThreadPool.newCachedThreadPool("RedissonExecutor");
            config.setExecutor(pool.getExecutorService());
            config.setNettyThreads(LC.redis_netty_threads.intValue());
            ClusterServersConfig clusterServersConfig = config.useClusterServers();
            clusterServersConfig.setScanInterval(LC.redis_cluster_scan_interval.intValue());
            clusterServersConfig.addNodeAddress(uri.split(" "));
            clusterServersConfig.setSubscriptionConnectionPoolSize(LC.redis_subscription_connection_pool_size.intValue());
            clusterServersConfig.setSubscriptionsPerConnection(LC.redis_subscriptions_per_connection.intValue());
            clusterServersConfig.setRetryInterval(LC.redis_retry_interval.intValue());
            clusterServersConfig.setTimeout(LC.redis_connection_timeout.intValue());
            clusterServersConfig.setRetryAttempts(LC.redis_num_retries.intValue());
            client = new RedissonRetryClient(Redisson.create(config));
        } catch (Exception ex) {
            ZimbraLog.system.fatal("Cannot setup RedissonClient to connect to %s", uri, ex);
            throw ex;
        }
    }

    public static RedissonClientHolder getInstance() {
        return  InstanceHolder.instance;
    }

    public RedissonClient getRedissonClient() {
        return client;
    }
}
