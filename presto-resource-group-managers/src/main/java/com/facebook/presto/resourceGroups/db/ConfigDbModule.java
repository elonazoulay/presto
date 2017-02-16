package com.facebook.presto.resourceGroups.db;

import com.facebook.presto.resourceGroups.db.DatabaseUtil.DaoSupplier;
import com.google.common.reflect.TypeParameter;
import com.google.common.reflect.TypeToken;
import com.google.inject.Binder;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.TypeLiteral;
import org.skife.jdbi.v2.IDBI;

import javax.inject.Inject;
import javax.inject.Provider;

import java.lang.reflect.Type;

import static com.google.common.base.Preconditions.checkState;
import static io.airlift.configuration.ConfigBinder.configBinder;
import static java.util.Objects.requireNonNull;

/**
 * Copyright 2017-present Facebook. All Rights Reserved.
 */
public class ConfigDbModule
    implements Module
{

    @Override
    public void configure(Binder binder)
    {
        configBinder(binder).bindConfig(DbResourceGroupConfig.class);

    }

    public static <B, T extends B> void bindDaoSupplier(Binder binder, Class<B> baseType, Class<T> type)
    {
        binder.bind(daoSupplierTypeToken(baseType))
                .toProvider(new DaoSupplierProvider<>(type))
                .in(Scopes.SINGLETON);
    }

    @SuppressWarnings("unchecked")
    private static <T> TypeLiteral<DaoSupplier<? extends T>> daoSupplierTypeToken(Class<T> type)
    {
        Type javaType = new TypeToken<DaoSupplier<T>>() {}
                .where(new TypeParameter<T>() {}, TypeToken.of(type))
                .getType();
        return (TypeLiteral<DaoSupplier<? extends T>>) TypeLiteral.get(javaType);
    }

    private static class DaoSupplierProvider<T>
            implements Provider<DaoSupplier<T>>
    {
        private final Class<T> type;
        private Injector injector;

        public DaoSupplierProvider(Class<T> type)
        {
            this.type = requireNonNull(type, "type is null");
        }

        @Inject
        public void setInjector(Injector injector)
        {
            this.injector = injector;
        }

        @Override
        public DaoSupplier<T> get()
        {
            checkState(injector != null, "injector was not set");
            IDBI dbi = injector.getInstance(Key.get(IDBI.class, ForResourceGroupsConfig.class));
            return new DaoSupplier<>(dbi, type);
        }
    }

        
}
