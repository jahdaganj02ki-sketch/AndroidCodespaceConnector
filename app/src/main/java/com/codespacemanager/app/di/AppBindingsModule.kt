package com.codespacemanager.app.di

import com.codespacemanager.app.data.local.assets.AssetDevContainerScriptProvider
import com.codespacemanager.app.data.local.datastore.SettingsStore
import com.codespacemanager.app.data.remote.auth.AuthRepositoryImpl
import com.codespacemanager.app.data.remote.editor.EditorHealthClient
import com.codespacemanager.app.data.remote.openvsx.OpenVsxClient
import com.codespacemanager.app.data.repository.CodespaceRepositoryImpl
import com.codespacemanager.app.data.repository.RepoRepositoryImpl
import com.codespacemanager.app.data.repository.TunnelGatewayImpl
import com.codespacemanager.app.domain.gateway.AuthGateway
import com.codespacemanager.app.domain.gateway.CodespaceGateway
import com.codespacemanager.app.domain.gateway.EditorHealthGateway
import com.codespacemanager.app.domain.gateway.OpenVsxGateway
import com.codespacemanager.app.domain.gateway.PortGateway
import com.codespacemanager.app.domain.gateway.RepoGateway
import com.codespacemanager.app.domain.gateway.SettingsGateway
import com.codespacemanager.app.domain.usecase.DevContainerScriptProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bindAuthGateway(impl: AuthRepositoryImpl): AuthGateway

    @Binds
    @Singleton
    abstract fun bindRepoGateway(impl: RepoRepositoryImpl): RepoGateway

    @Binds
    @Singleton
    abstract fun bindCodespaceGateway(impl: CodespaceRepositoryImpl): CodespaceGateway

    @Binds
    @Singleton
    abstract fun bindPortGateway(impl: TunnelGatewayImpl): PortGateway

    @Binds
    @Singleton
    abstract fun bindSettingsGateway(impl: SettingsStore): SettingsGateway

    @Binds
    @Singleton
    abstract fun bindOpenVsxGateway(impl: OpenVsxClient): OpenVsxGateway

    @Binds
    @Singleton
    abstract fun bindEditorHealthGateway(impl: EditorHealthClient): EditorHealthGateway

    @Binds
    @Singleton
    abstract fun bindScriptProvider(impl: AssetDevContainerScriptProvider): DevContainerScriptProvider
}
