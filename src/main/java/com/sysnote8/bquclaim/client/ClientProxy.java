package com.sysnote8.bquclaim.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.sysnote8.bquclaim.core.CommonProxy;

/**
 * Call from only client
 */
@SideOnly(Side.CLIENT)
public class ClientProxy extends CommonProxy {}
