package com.bdmajora.impetus.impl.platform;

import com.cleanroommc.client.sdl.SDL;
import com.cleanroommc.client.sdl.Window;
import com.cleanroommc.client.sdl.video.Display;
import com.cleanroommc.client.sdl.video.DisplayMode;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLVideo;
import org.lwjgl.sdl.SDL_DisplayMode;
import org.mockito.MockedStatic;

import java.nio.IntBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameWindowTest {
    @Test
    void queriesReadTheSdlWindowAndStaySafeBeforeCreation() {
        Window window = mock(Window.class);
        try (MockedStatic<SDL> sdl = mockStatic(SDL.class); MockedStatic<SDLVideo> video = mockStatic(SDLVideo.class)) {
            assertEquals(0, GameWindow.handle());
            assertFalse(GameWindow.isFocused());
            assertFalse(GameWindow.isMinimized());
            assertFalse(GameWindow.isContextCurrent());
            assertFalse(GameWindow.isBorderless());
            assertFalse(GameWindow.isFullscreen());
            assertEquals(0, GameWindow.fullscreenModes().length);
            assertNull(GameWindow.desktopMode());
            assertFalse(GameWindow.supportsAdaptiveSync());
            GameWindow.setVsync(true);
            GameWindow.setBorderless(true);
            sdl.when(SDL::window).thenReturn(window);
            when(window.handle()).thenReturn(42L);
            assertEquals(42L, GameWindow.handle());
            when(window.focused()).thenReturn(true);
            assertTrue(GameWindow.isFocused());
            when(window.minimized()).thenReturn(true);
            assertTrue(GameWindow.isMinimized());
            when(window.minimized()).thenReturn(false);
            when(window.hidden()).thenReturn(true);
            assertTrue(GameWindow.isMinimized());
            video.when(SDLVideo::SDL_GL_GetCurrentWindow).thenReturn(42L);
            assertTrue(GameWindow.isContextCurrent());
            video.when(() -> SDLVideo.SDL_GL_SetSwapInterval(-1)).thenReturn(true);
            assertTrue(GameWindow.setSwapInterval(-1));
            GameWindow.setVsync(true);
            verify(window).vsync(true);
            GameWindow.setBorderless(true);
            verify(window).fullscreenMode(null);
            GameWindow.setBorderless(false);
            verify(window).fullscreen(false);
        }
    }

    @Test
    void adaptiveProbeRestoresTheActualIntervalWithoutChangingTheSetting() {
        Window window = mock(Window.class);
        when(window.handle()).thenReturn(42L);
        try (MockedStatic<SDL> sdl = mockStatic(SDL.class); MockedStatic<SDLVideo> video = mockStatic(SDLVideo.class)) {
            sdl.when(SDL::window).thenReturn(window);
            video.when(SDLVideo::SDL_GL_GetCurrentWindow).thenReturn(42L);
            assertFalse(GameWindow.supportsAdaptiveSync());
            video.when(() -> SDLVideo.SDL_GL_GetSwapInterval(any(IntBuffer.class))).thenAnswer(inv -> {
                inv.<IntBuffer>getArgument(0).put(0, 0);
                return true;
            });
            video.when(() -> SDLVideo.SDL_GL_SetSwapInterval(-1)).thenReturn(true);
            assertTrue(GameWindow.supportsAdaptiveSync());
            video.verify(() -> SDLVideo.SDL_GL_SetSwapInterval(0));
            video.when(() -> SDLVideo.SDL_GL_SetSwapInterval(-1)).thenReturn(false);
            assertFalse(GameWindow.supportsAdaptiveSync());
        }
    }

    @Test
    void fullscreenUsesModesFromTheCurrentDisplayAndDistinguishesDesktopFullscreen() {
        Window window = mock(Window.class);
        Display display = mock(Display.class);
        DisplayMode mode = new DisplayMode(7, 0, 1920, 1080, 1, 144);
        when(window.handle()).thenReturn(42L);
        when(window.display()).thenReturn(display);
        when(display.modes()).thenReturn(List.of(mode));
        when(display.desktopMode()).thenReturn(mode);
        try (MockedStatic<SDL> sdl = mockStatic(SDL.class); MockedStatic<SDLVideo> video = mockStatic(SDLVideo.class)) {
            sdl.when(SDL::window).thenReturn(window);
            assertArrayEquals(new DisplayMode[] {mode}, GameWindow.fullscreenModes());
            assertSame(mode, GameWindow.desktopMode());
            assertFalse(GameWindow.setFullscreenMode(mode));
            when(window.fullscreen()).thenReturn(true);
            assertTrue(GameWindow.isFullscreen());
            assertTrue(GameWindow.isBorderless());
            assertFalse(GameWindow.setFullscreenMode(mode));
            video.when(() -> SDLVideo.SDL_GetWindowFullscreenMode(42L)).thenReturn(mock(SDL_DisplayMode.class));
            assertFalse(GameWindow.isBorderless());
            assertTrue(GameWindow.setFullscreenMode(new DisplayMode(1, 0, 1920, 1080, 1, 144)));
            verify(window).fullscreenMode(mode);
            assertFalse(GameWindow.setFullscreenMode(new DisplayMode(1, 0, 640, 480, 1, 60)));
            when(window.borderless()).thenReturn(true);
            assertTrue(GameWindow.isBorderless());
        }
    }
}
